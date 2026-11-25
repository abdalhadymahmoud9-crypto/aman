package com.aman.filter;
import java.io.*;
import java.net.*;
import java.util.Arrays;

class TestDns {
    // Build a minimal IPv4/UDP/DNS query for name (A record)
    static byte[] buildQuery(String name, int id) throws Exception {
        ByteArrayOutputStream dns = new ByteArrayOutputStream();
        dns.write(id >> 8); dns.write(id & 0xFF);
        dns.write(0x01); dns.write(0x00);           // RD
        dns.write(0); dns.write(1);                  // QDCOUNT 1
        for (int i = 0; i < 8; i++) dns.write(0);
        for (String label : name.split("\\.")) {
            dns.write(label.length());
            dns.write(label.getBytes("ASCII"));
        }
        dns.write(0);                                 // end of name
        dns.write(0); dns.write(1);                   // QTYPE A
        dns.write(0); dns.write(1);                   // QCLASS IN
        byte[] dnsBytes = dns.toByteArray();
        int udpLen = 8 + dnsBytes.length;
        int total = 20 + udpLen;
        ByteArrayOutputStream pkt = new ByteArrayOutputStream();
        pkt.write(0x45); pkt.write(0);                // v4, ihl 5
        pkt.write(total >> 8); pkt.write(total & 0xFF);
        pkt.write(0); pkt.write(0); pkt.write(0); pkt.write(0); // id, flags
        pkt.write(64); pkt.write(17);                 // TTL, UDP
        pkt.write(0); pkt.write(0);                   // checksum (skip)
        pkt.write(new byte[]{10,0,0,1});              // src
        pkt.write(new byte[]{10,0,0,2});              // dst
        pkt.write(0xC0 >> 8); pkt.write(53 & 0xFF);   // src port 49152? just put 12345
        byte[] p = pkt.toByteArray();
        p[20] = (byte)(12345 >> 8); p[21] = (byte)12345;
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(p);
        full.write((12345 >> 8) & 0xFF); full.write(12345 & 0xFF); // oops - handle below
        return null;
    }

    public static void main(String[] a) throws Exception {
        // simpler: construct packet manually in one array
        String name = "sub.example.com";
        ByteArrayOutputStream dnsB = new ByteArrayOutputStream();
        int id = 0x1A2B;
        dnsB.write(new byte[]{(byte)(id>>8),(byte)id, 0x01,0x00, 0,1, 0,0, 0,0, 0,0});
        for (String label : name.split("\\.")) { dnsB.write(label.length()); dnsB.write(label.getBytes("ASCII")); }
        dnsB.write(new byte[]{0, 0,1, 0,1});
        byte[] dns = dnsB.toByteArray();
        int udpLen = 8 + dns.length, total = 20 + udpLen;
        ByteArrayOutputStream pB = new ByteArrayOutputStream();
        pB.write(new byte[]{0x45,0,(byte)(total>>8),(byte)total, 0,0,0,0, 64,17, 0,0, 10,0,0,1, 10,0,0,2});
        pB.write(new byte[]{(byte)(12345>>8),(byte)12345, 0,53, (byte)(udpLen>>8),(byte)udpLen, 0,0});
        pB.write(dns);
        byte[] pkt = pB.toByteArray();

        // 1. parse
        DnsFilter.Query q = DnsFilter.parse(pkt, pkt.length);
        check("parse qname", q != null && q.qname.equals("sub.example.com"));
        check("parse id", q != null && q.id == id);
        check("question end", q != null && q.questionEnd == dns.length);

        // 2. nxdomain
        byte[] nx = DnsFilter.nxdomain(pkt, pkt.length, q);
        int nxDnsOff = 20 + 8;
        int flags = ((nx[nxDnsOff+2]&0xFF)<<8)|(nx[nxDnsOff+3]&0xFF);
        check("nxdomain QR set", (flags & 0x8000) != 0);
        check("nxdomain RCODE=3", (flags & 0xF) == 3);
        check("nxdomain id echoed", ((nx[nxDnsOff]&0xFF)<<8|(nx[nxDnsOff+1]&0xFF)) == id);
        check("nxdomain addrs swapped", nx[12]==10&&nx[13]==0&&nx[14]==0&&nx[15]==2 && nx[16]==10&&nx[19]==1);
        check("nxdomain ports swapped", ((nx[20]&0xFF)<<8|(nx[21]&0xFF))==53 && ((nx[22]&0xFF)<<8|(nx[23]&0xFF))==12345);
        int ipcs = ((nx[10]&0xFF)<<8)|(nx[11]&0xFF);
        check("nxdomain ip checksum nonzero", ipcs != 0);
        // verify checksum correctness
        int sum=0; for (int i=0;i<20;i+=2) sum+=((nx[i]&0xFF)<<8)|(nx[i+1]&0xFF);
        while((sum>>16)!=0) sum=(sum&0xFFFF)+(sum>>16);
        check("nxdomain ip checksum valid", (sum & 0xFFFF) == 0xFFFF);
        // question echoed
        check("question echoed", Arrays.equals(Arrays.copyOfRange(nx, nxDnsOff+12, nx.length), Arrays.copyOfRange(dns, 12, dns.length)));

        // 3. wrapResponse roundtrip
        byte[] wrapped = DnsFilter.wrapResponse(pkt, dns);
        check("wrap length", wrapped.length == 20+8+dns.length);
        check("wrap payload", Arrays.equals(Arrays.copyOfRange(wrapped, 28, wrapped.length), dns));
        check("wrap ports swapped", ((wrapped[20]&0xFF)<<8|(wrapped[21]&0xFF))==53);

        // 4. real upstream forward (safe test: resolve example.com via 1.1.1.1)
        DatagramSocket s = new DatagramSocket();
        s.setSoTimeout(4000);
        byte[] resp = DnsFilter.forward(s, dns, InetAddress.getByName("1.1.1.1"), 53);
        int rid = ((resp[0]&0xFF)<<8)|(resp[1]&0xFF);
        int rcode = resp[3] & 0xF;
        check("upstream response id matches", rid == id);
        check("upstream resolves example.com (rcode 0)", rcode == 0);
        s.close();

        // 5. malformed packets rejected, not crash
        check("null-safe on garbage", DnsFilter.parse(new byte[]{1,2,3}, 3) == null);
        byte[] v6 = pkt.clone(); v6[0] = 0x60;
        check("ipv6 ignored", DnsFilter.parse(v6, v6.length) == null);
        byte[] tcp = pkt.clone(); tcp[9] = 6;
        check("non-UDP ignored", DnsFilter.parse(tcp, tcp.length) == null);

        System.out.println("ALL DNS TESTS DONE");
    }
    static int pass=0, fail=0;
    static void check(String n, boolean ok){ System.out.println((ok?"PASS":"FAIL")+" | "+n); if(!ok){fail++; System.exit(1);} }
}
