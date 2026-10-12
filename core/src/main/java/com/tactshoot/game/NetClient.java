package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * WebSocket client for the TactShoot server. Receives messages on the socket
 * thread and hands them to the render thread through an inbox queue.
 */
public class NetClient {

    public interface Listener {
        void onOpen();

        void onClose(String reason);

        void onError(String msg);

        /** Called on the render thread with a parsed JSON message. */
        void onMessage(String type, JsonValue data);
    }

    private final Listener listener;
    private WebSocketClient ws;
    private final Object lock = new Object();
    private final Array<JsonValue> inbox = new Array<>();
    private boolean open;
    private String lastError = "";
    private float rtt = -1f;
    private long pingSentAt;
    private float pingTimer = 2f;
    private String url = "";

    private volatile Listener activeListener;

    public NetClient(Listener listener) {
        this.listener = listener;
        this.activeListener = listener;
    }

    /** Swap the message listener (used when the connection moves between screens). */
    public void setListener(Listener l) {
        this.activeListener = l;
    }

    public void connect(String url) {
        this.url = url;
        close();
        try {
            final NetClient self = this;
            ws = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    synchronized (lock) {
                        open = true;
                    }
                    Gdx.app.postRunnable(() -> {
                        Listener l = activeListener;
                        if (l != null) l.onOpen();
                    });
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonValue v = new JsonReader().parse(message);
                        synchronized (lock) {
                            inbox.add(v);
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    synchronized (lock) {
                        open = false;
                    }
                    Gdx.app.postRunnable(() -> {
                        Listener l = activeListener;
                        if (l != null) l.onClose(reason + " (code " + code + ")");
                    });
                }

                @Override
                public void onError(Exception ex) {
                    lastError = ex.getMessage();
                    Gdx.app.postRunnable(() -> {
                        Listener l = activeListener;
                        if (l != null) l.onError(ex.getMessage());
                    });
                }
            };
            if (url.startsWith("wss://")) {
                try {
                    javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                    sslContext.init(null, null, null);
                    ws.setSocketFactory(sslContext.getSocketFactory());
                } catch (Exception e) {
                    Gdx.app.error("TactShoot", "SSL setup failed: " + e.getMessage());
                }
            }
            ws.setConnectionLostTimeout(25);
            ws.connect();
        } catch (Exception e) {
            lastError = e.getMessage();
            Listener l = activeListener;
            if (l != null) l.onError(e.getMessage());
        }
    }

    public void close() {
        if (ws != null) {
            try {
                ws.close(1000, "bye");
            } catch (Exception ignored) {
            }
            ws = null;
        }
        synchronized (lock) {
            open = false;
            inbox.clear();
        }
    }

    public boolean isOpen() {
        synchronized (lock) {
            return open;
        }
    }

    public String lastError() {
        return lastError;
    }

    public float rtt() {
        return rtt;
    }

    public String url() {
        return url;
    }

    /** Drain the inbox on the render thread. */
    public void update() {
        JsonValue v;
        while (true) {
            synchronized (lock) {
                if (inbox.size == 0) break;
                v = inbox.removeIndex(0);
            }
            String type = v.getString("t", "");
            Listener l = activeListener;
            if (l != null) {
                l.onMessage(type, v);
            }
        }
        // ping
        pingTimer -= Gdx.graphics.getDeltaTime();
        if (isOpen() && pingTimer <= 0f) {
            pingTimer = 4f;
            pingSentAt = System.currentTimeMillis();
            send(obj("t", "ping", "tm", pingSentAt));
        }
        if (rtt >= 0f && System.currentTimeMillis() - pingSentAt > 8000f) {
            rtt = -1f;
        }
    }

    public void send(JsonValue v) {
        WebSocketClient s = ws;
        if (s != null && s.isOpen()) {
            try {
                s.send(v.toJson(JsonWriter.OutputType.json));
            } catch (Exception ignored) {
            }
        }
    }

    /** Compact object builder. */
    public static JsonValue obj(Object... kv) {
        JsonValue o = new JsonValue(JsonValue.ValueType.object);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Number) {
                o.addChild(kv[i].toString(), new JsonValue(((Number) v).doubleValue()));
            } else if (v instanceof Boolean) {
                o.addChild(kv[i].toString(), new JsonValue((Boolean) v));
            } else if (v instanceof JsonValue) {
                o.addChild(kv[i].toString(), (JsonValue) v);
            } else {
                o.addChild(kv[i].toString(), new JsonValue(String.valueOf(v)));
            }
        }
        return o;
    }

    public static JsonValue arr3(float x, float y, float z) {
        JsonValue a = new JsonValue(JsonValue.ValueType.array);
        a.addChild(new JsonValue(x));
        a.addChild(new JsonValue(y));
        a.addChild(new JsonValue(z));
        return a;
    }

    public void handlePong(long tm) {
        rtt = (System.currentTimeMillis() - tm) / 2f;
    }
}
