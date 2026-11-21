package id.apkx;

import org.json.JSONArray;
import org.json.JSONObject;

public final class McpHandler {
    private McpHandler() {}

    public static String handle(String body) {
        try {
            JSONObject req = new JSONObject(body);
            String method = req.optString("method", "");
            if (method.startsWith("notifications/")) return "";

            JSONObject res = new JSONObject();
            res.put("jsonrpc", "2.0");
            Object id = req.opt("id");
            res.put("id", id == null ? JSONObject.NULL : id);
            try {
                res.put("result", dispatch(method, req.optJSONObject("params")));
            } catch (Exception e) {
                JSONObject err = new JSONObject();
                err.put("code", -32603);
                err.put("message", String.valueOf(e.getMessage()));
                res.put("error", err);
            }
            return res.toString();
        } catch (Exception e) {
            return parseError(e);
        }
    }

    private static Object dispatch(String method, JSONObject params) throws Exception {
        switch (method) {
            case "initialize": {
                JSONObject r = new JSONObject();
                String v = params == null ? "2025-06-18" : params.optString("protocolVersion", "2025-06-18");
                r.put("protocolVersion", v);
                r.put("capabilities", new JSONObject().put("tools", new JSONObject()));
                r.put("serverInfo", new JSONObject().put("name", "apkx").put("version", "0.1.0"));
                return r;
            }
            case "ping":
                return new JSONObject();
            case "tools/list":
                return new JSONObject().put("tools", Tools.schemas());
            case "resources/list":
                return new JSONObject().put("resources", new JSONArray());
            case "prompts/list":
                return new JSONObject().put("prompts", new JSONArray());
            case "tools/call": {
                if (params == null) throw new IllegalArgumentException("params kosong");
                String name = params.optString("name", "");
                JSONObject args = params.optJSONObject("arguments");
                if (args == null) args = new JSONObject();
                return Tools.call(name, args);
            }
            default:
                throw new IllegalArgumentException("method tidak dikenal: " + method);
        }
    }

    private static String parseError(Exception e) {
        try {
            JSONObject res = new JSONObject();
            res.put("jsonrpc", "2.0");
            res.put("id", JSONObject.NULL);
            JSONObject err = new JSONObject();
            err.put("code", -32700);
            err.put("message", "JSON tidak valid: " + e);
            res.put("error", err);
            return res.toString();
        } catch (Exception ignored) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"parse error\"}}";
        }
    }
}
