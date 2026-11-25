// Aman Web Filter - background service worker (MV3)
// Transparent, voluntary filtering. No network calls, no data leaves the browser.
//
// Rule budget safety: Chrome guarantees 30,000 static rules per extension
// (GUARANTEED_MINIMUM_STATIC_RULES); more may be available from a shared
// global pool. We ship a baseline ruleset (28,000 rules, always enabled)
// plus extended sets that we try to enable at runtime. If the budget is not
// available, the baseline keeps working and the status reports which sets
// are active. Nothing fails silently.

const BASELINE_ID = "rules_1";
const EXTENDED_IDS = ["rules_2", "rules_3", "rules_4"];
const DYN_ID_BASE = 1;

async function getState() {
  return chrome.storage.local.get({
    enabled: true, customBlocked: [], customAllowed: [], extendedActive: []
  });
}

function domainToRule(domain, id, actionType, priority) {
  return { id, priority, action: { type: actionType },
           condition: { urlFilter: "||" + domain + "^" } };
}

async function applyDynamicRules(enabled) {
  const { customBlocked, customAllowed } = await getState();
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  const removeRuleIds = existing.map(r => r.id);
  const addRules = [];
  if (enabled) {
    customBlocked.forEach((d, i) => addRules.push(domainToRule(d, DYN_ID_BASE + i, "block", 1)));
    customAllowed.forEach((d, i) => addRules.push(domainToRule(d, DYN_ID_BASE + 10000 + i, "allow", 3)));
  }
  await chrome.declarativeNetRequest.updateDynamicRules({ removeRuleIds, addRules });
}

// Try to enable extended rulesets one by one; stop at the first failure and
// record what is active. Baseline is declared enabled in the manifest.
async function ensureExtended() {
  const active = [];
  for (const id of EXTENDED_IDS) {
    try {
      await chrome.declarativeNetRequest.updateEnabledRulesets({ enableRulesetIds: [id] });
      active.push(id);
    } catch (e) {
      break; // rule budget exhausted - baseline + whatever is active keeps working
    }
  }
  await chrome.storage.local.set({ extendedActive: active });
  return active;
}

async function applyEnabled(enabled) {
  if (enabled) {
    await chrome.declarativeNetRequest.updateEnabledRulesets({ enableRulesetIds: [BASELINE_ID] });
    await ensureExtended();
  } else {
    await chrome.declarativeNetRequest.updateEnabledRulesets(
      { disableRulesetIds: [BASELINE_ID, ...EXTENDED_IDS] });
  }
  await applyDynamicRules(enabled);
  await refreshBadge();
}

async function refreshBadge() {
  const { enabled } = await getState();
  await chrome.action.setBadgeText({ text: enabled ? "ON" : "OFF" });
  await chrome.action.setBadgeBackgroundColor({ color: enabled ? "#1a7f4b" : "#8a8a8a" });
}

chrome.runtime.onInstalled.addListener(async () => {
  await ensureExtended();
  await refreshBadge();
});
chrome.runtime.onStartup.addListener(async () => { await refreshBadge(); });

let sessionBlocked = 0;
if (chrome.declarativeNetRequest.onRuleMatchedDebug) {
  chrome.declarativeNetRequest.onRuleMatchedDebug.addListener(() => { sessionBlocked++; });
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      if (msg.type === "getStatus") {
        const s = await getState();
        const enabledSets = await chrome.declarativeNetRequest.getEnabledRulesets();
        sendResponse({ ok: true, ...s, sessionBlocked,
                       rulesetsActive: enabledSets.length, rulesetsTotal: 1 + EXTENDED_IDS.length });
      } else if (msg.type === "setEnabled") {
        await chrome.storage.local.set({ enabled: !!msg.enabled });
        await applyEnabled(!!msg.enabled);
        sendResponse({ ok: true, enabled: !!msg.enabled });
      } else if (msg.type === "addCustom") {
        const s = await getState();
        const key = msg.list === "allowed" ? "customAllowed" : "customBlocked";
        const other = msg.list === "allowed" ? "customBlocked" : "customAllowed";
        s[key] = s[key].filter(d => d !== msg.domain);
        s[key].push(msg.domain);
        s[other] = s[other].filter(d => d !== msg.domain);
        await chrome.storage.local.set({ [key]: s[key], [other]: s[other] });
        await applyDynamicRules(s.enabled);
        sendResponse({ ok: true, customBlocked: s.customBlocked, customAllowed: s.customAllowed });
      } else if (msg.type === "removeCustom") {
        const s = await getState();
        const key = msg.list === "allowed" ? "customAllowed" : "customBlocked";
        s[key] = s[key].filter(d => d !== msg.domain);
        await chrome.storage.local.set({ [key]: s[key] });
        await applyDynamicRules(s.enabled);
        sendResponse({ ok: true, customBlocked: s.customBlocked, customAllowed: s.customAllowed });
      } else {
        sendResponse({ ok: false, error: "unknown message" });
      }
    } catch (e) {
      sendResponse({ ok: false, error: String(e) });
    }
  })();
  return true;
});
