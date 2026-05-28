package com.takpack.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class DataStore {
    private static final String PREF = "tak_sms_store_v7";
    private static final String RULES = "rules";
    private static final String SMS = "sms";
    private static final String ENABLED = "enabled";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(ENABLED, v).apply();
    }

    public static boolean isEnabled(Context c) {
        return sp(c).getBoolean(ENABLED, true);
    }

    public static JSONArray getRulesArray(Context c) {
        try { return new JSONArray(sp(c).getString(RULES, "[]")); }
        catch(Exception e) { return new JSONArray(); }
    }

    public static String getRules(Context c) { return getRulesArray(c).toString(); }

    public static boolean saveRule(Context c, String ruleJson) {
        try {
            JSONObject rule = new JSONObject(ruleJson);
            String id = rule.optString("id", "");
            if (id.trim().isEmpty()) {
                id = String.valueOf(System.currentTimeMillis());
                rule.put("id", id);
            }
            if (!rule.has("enabled")) rule.put("enabled", true);
            if (!rule.has("senders")) rule.put("senders", new JSONArray());

            JSONArray oldArr = getRulesArray(c);
            JSONArray newArr = new JSONArray();
            boolean found = false;

            for (int i = 0; i < oldArr.length(); i++) {
                JSONObject old = oldArr.getJSONObject(i);
                if (id.equals(old.optString("id"))) {
                    newArr.put(rule);
                    found = true;
                } else {
                    newArr.put(old);
                }
            }
            if (!found) newArr.put(rule);
            sp(c).edit().putString(RULES, newArr.toString()).apply();
            return true;
        } catch(Exception e) { return false; }
    }

    public static JSONArray getSmsArray(Context c) {
        try { return new JSONArray(sp(c).getString(SMS, "[]")); }
        catch(Exception e) { return new JSONArray(); }
    }

    public static String getSms(Context c) { return getSmsArray(c).toString(); }

    public static void addSms(Context c, String sender, String body, long time) {
        try {
            JSONArray oldArr = getSmsArray(c);
            JSONArray newArr = new JSONArray();

            JSONObject matched = matchRule(c, sender, body);
            JSONObject m = new JSONObject();
            m.put("id", String.valueOf(System.currentTimeMillis()));
            m.put("sender", sender == null ? "" : sender);
            m.put("body", body == null ? "" : body);
            m.put("time", time);
            m.put("rule", matched.optString("rule", "عمومی"));
            m.put("senderNote", matched.optString("senderNote", ""));
            newArr.put(m);

            int limit = Math.min(oldArr.length(), 99);
            for (int i = 0; i < limit; i++) newArr.put(oldArr.getJSONObject(i));
            sp(c).edit().putString(SMS, newArr.toString()).apply();
        } catch(Exception ignored) {}
    }

    private static String normalize(String x) {
        if (x == null) return "";
        return x.replace(" ", "").replace("-", "").replace("+98", "0").trim().toLowerCase();
    }

    public static JSONObject matchRule(Context c, String sender, String body) {
        JSONObject result = new JSONObject();
        try {
            String s = sender == null ? "" : sender;
            String b = body == null ? "" : body;
            String ns = normalize(s);
            String all = (s + " " + b).toLowerCase();

            JSONArray rules = getRulesArray(c);
            for (int i = 0; i < rules.length(); i++) {
                JSONObject r = rules.getJSONObject(i);
                if (!r.optBoolean("enabled", true)) continue;

                boolean senderOk = false;
                String senderNote = "";
                JSONArray senders = r.optJSONArray("senders");

                if (senders == null || senders.length() == 0) {
                    senderOk = true;
                } else {
                    for (int j = 0; j < senders.length(); j++) {
                        JSONObject it = senders.getJSONObject(j);
                        String n = normalize(it.optString("number", ""));
                        if (n.length() == 0 || ns.contains(n) || n.contains(ns)) {
                            senderOk = true;
                            senderNote = it.optString("note", "");
                            break;
                        }
                    }
                }

                String keywords = r.optString("keywords", "").trim().toLowerCase();
                boolean keyOk = keywords.isEmpty() || "همه".equals(keywords);
                if (!keyOk) {
                    String[] parts = keywords.split("[,،\\s]+");
                    for (String p : parts) {
                        p = p.trim();
                        if (p.length() > 0 && all.contains(p)) { keyOk = true; break; }
                    }
                }

                if (senderOk && keyOk) {
                    result.put("rule", r.optString("name", "قانون ذخیره‌شده"));
                    result.put("senderNote", senderNote);
                    return result;
                }
            }

            if (all.contains("بانک") || all.contains("کارت") || all.contains("تراکنش")) result.put("rule", "بانک‌ها");
            else if (all.contains("otp") || all.contains("کد") || all.contains("رمز")) result.put("rule", "OTP");
            else if (all.contains("تخفیف") || all.contains("فروش")) result.put("rule", "تبلیغات");
            else result.put("rule", "عمومی");
            result.put("senderNote", "");
        } catch(Exception ignored) {}
        return result;
    }
}
