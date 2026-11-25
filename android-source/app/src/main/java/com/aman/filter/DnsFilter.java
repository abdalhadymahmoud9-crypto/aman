package com.aman.filter;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Minimal IPv4/UDP/DNS handling for the local tunnel.
 * Only DNS queries (UDP dst port 53) enter the tunnel; everything else on the
 * phone keeps its normal direct route, so there is no traffic blackhole.
 */
public class DnsFilter {
    static final int UDP = 17;

    /** Parsed query: qname lowercase, offset+length of the question section, id. */
    static class Query {
        String qname;
        int questionEnd; // offset in payload just past the first question
        int id;
        int flags;
    }

    /** Returns the parsed DNS question, or null if the packet is not a plain IPv4 UDP DNS query. */
    static Query parse(byte[] pkt, int len) {
        try {
            if (len < 20 + 8 + 12) return null;
            int version = (pkt[0] >> 4) & 0xF;
            if (version != 4) return null;              // IPv6 ignored (MVP)
            int ihl = (pkt[0] & 0xF) * 4;
            if (len < ihl + 8) return null;
            if ((pkt[9] & 0xFF) != UDP) return null;
            int dstPort = ((pkt[ihl + 2] & 0xFF) << 8) | (pkt[ihl + 3] & 0xFF);
            if (dstPort != 53) return null;
            int dnsOff = ihl + 8;
            int dnsLen = len - dnsOff;
            if (dnsLen < 12) return null;
            Query q = new Query();
            q.id = ((pkt[dnsOff] & 0xFF) << 8) | (pkt[dnsOff + 1] & 0xFF);
            q.flags = ((pkt[dnsOff + 2] & 0xFF) << 8) | (pkt[dnsOff + 3] & 0xFF);
            int qd = ((pkt[dnsOff + 4] & 0xFF) << 8) | (pkt[dnsOff + 5] & 0xFF);
            if (qd != 1) return null;
            StringBuilder name = new StringBuilder();
            int p = dnsOff + 12;
            while (true) {
                if (p >= len) return null;
                int l = pkt[p] & 0xFF;
                if (l == 0) { p++; break; }
                if ((l & 0xC0) != 0) return null;       // compressed name in query: unsupported
                if (p + 1 + l > len) return null;
                if (name.length() > 0) name.append('.');
                for (int i = 0; i < l; i++) name.append((char) (pkt[p + 1 + i] & 0xFF));
                p += 1 + l;
            }
            if (p + 4 > len) return null;               // qtype + qclass
            q.qname = name.toString().toLowerCase();
            q.questionEnd = p + 4 - dnsOff;             // relative to DNS payload start
            return q;
        } catch (Exception e) {
            return null;
        }
    }

    /** Builds an NXDOMAIN response packet echoing the question, swapping addresses/ports. */
    static byte[] nxdomain(byte[] pkt, int len, Query q) {
        int ihl = (pkt[0] & 0xF) * 4;
        int dnsOff = ihl + 8;
        int dnsRespLen = q.questionEnd;                  // header + original question (questionEnd is relative to DNS start)
        int udpLen = 8 + dnsRespLen;
        int totalLen = ihl + udpLen;
        byte[] out = new byte[totalLen];
        System.arraycopy(pkt, 0, out, 0, ihl + 8);      // ip + udp headers, fixed below
        // IP header
        out[2] = (byte) (totalLen >> 8); out[3] = (byte) totalLen;
        System.arraycopy(pkt, 12, out, 16, 4);          // src <- old dst
        System.arraycopy(pkt, 16, out, 12, 4);          // dst <- old src
        out[8] = 64;                                    // TTL
        out[10] = 0; out[11] = 0;                       // clear checksum
        int sum = 0;
        for (int i = 0; i < ihl; i += 2) sum += ((out[i] & 0xFF) << 8) | (out[i + 1] & 0xFF);
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        int csum = ~sum & 0xFFFF;
        out[10] = (byte) (csum >> 8); out[11] = (byte) csum;
        // UDP header
        System.arraycopy(pkt, ihl, out, ihl + 2, 2);    // src port <- old dst port
        System.arraycopy(pkt, ihl + 2, out, ihl, 2);    // dst port <- old src port
        out[ihl + 4] = (byte) (udpLen >> 8); out[ihl + 5] = (byte) udpLen;
        out[ihl + 6] = 0; out[ihl + 7] = 0;             // UDP checksum 0 (valid for IPv4)
        // DNS header
        out[dnsOff] = (byte) (q.id >> 8); out[dnsOff + 1] = (byte) q.id;
        int flags = 0x8000 | (q.flags & 0x0100) | 0x0080 | 0x0003; // QR + RD + RA + NXDOMAIN
        out[dnsOff + 2] = (byte) (flags >> 8); out[dnsOff + 3] = (byte) flags;
        out[dnsOff + 4] = 0; out[dnsOff + 5] = 1;       // QDCOUNT 1
        for (int i = 6; i < 12; i++) out[dnsOff + i] = 0;
        System.arraycopy(pkt, dnsOff + 12, out, dnsOff + 12, q.questionEnd - 12);
        return out;
    }

    /** Forwards the raw DNS payload to the upstream resolver through a protected socket. */
    static byte[] forward(DatagramSocket socket, byte[] dnsPayload, InetAddress upstream, int port)
            throws Exception {
        DatagramPacket req = new DatagramPacket(dnsPayload, dnsPayload.length, upstream, port);
        socket.send(req);
        byte[] buf = new byte[1500];
        DatagramPacket resp = new DatagramPacket(buf, buf.length);
        socket.receive(resp); // timeout set by caller
        byte[] out = new byte[resp.getLength()];
        System.arraycopy(buf, 0, out, 0, out.length);
        return out;
    }

    /** Wraps an upstream DNS response payload back into an IP/UDP tunnel packet. */
    static byte[] wrapResponse(byte[] origPkt, byte[] dnsPayload) {
        int ihl = (origPkt[0] & 0xF) * 4;
        int udpLen = 8 + dnsPayload.length;
        int totalLen = ihl + udpLen;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(totalLen);
        bos.write(origPkt, 0, ihl + 8);
        byte[] out = bos.toByteArray();
        out[2] = (byte) (totalLen >> 8); out[3] = (byte) totalLen;
        System.arraycopy(origPkt, 12, out, 16, 4);
        System.arraycopy(origPkt, 16, out, 12, 4);
        out[8] = 64; out[10] = 0; out[11] = 0;
        int sum = 0;
        for (int i = 0; i < ihl; i += 2) sum += ((out[i] & 0xFF) << 8) | (out[i + 1] & 0xFF);
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        int csum = ~sum & 0xFFFF;
        out[10] = (byte) (csum >> 8); out[11] = (byte) csum;
        System.arraycopy(origPkt, ihl, out, ihl + 2, 2);
        System.arraycopy(origPkt, ihl + 2, out, ihl, 2);
        out[ihl + 4] = (byte) (udpLen >> 8); out[ihl + 5] = (byte) udpLen;
        out[ihl + 6] = 0; out[ihl + 7] = 0;
        byte[] head = out;
        byte[] full = new byte[totalLen];
        System.arraycopy(head, 0, full, 0, ihl + 8);
        System.arraycopy(dnsPayload, 0, full, ihl + 8, dnsPayload.length);
        return full;
    }
}
