package com.bearmod.loader.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bearmod.loader.model.Patch;
import com.bearmod.loader.network.NetworkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * WebSocket manager for real-time updates
 */
public class WebSocketManager {
    
    private static final String TAG = "WebSocketManager";
    
    // WebSocket server URL
    private static final String WEBSOCKET_URL = "wss://api.bearmod.com/ws";
    
    // Ping interval in seconds
    private static final long PING_INTERVAL = 30;
    
    // Reconnect parameters
    private static final long RECONNECT_DELAY = 5000; // 5 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    
    private Context context;
    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Listeners
    private final List<WebSocketEventListener> listeners = new ArrayList<>();
    
    /**
     * Constructor
     * @param context Application context
     */
    public WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        
        // Get OkHttpClient from NetworkManager
        this.client = NetworkManager.getInstance(context).getOkHttpClient().newBuilder()
                .pingInterval(PING_INTERVAL, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Connect to WebSocket server
     */
    public void connect() {
        if (isConnected) {
            Log.d(TAG, "WebSocket already connected");
            return;
        }
        
        try {
            // Create request
            Request request = new Request.Builder()
                    .url(WEBSOCKET_URL)
                    .build();
            
            // Create WebSocket listener
            BearWebSocketListener listener = new BearWebSocketListener();
            
            // Connect to WebSocket server
            webSocket = client.newWebSocket(request, listener);
            
            Log.d(TAG, "WebSocket connecting...");
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket: " + e.getMessage());
            scheduleReconnect();
        }
    }
    
    /**
     * Disconnect from WebSocket server
     */
    public void disconnect() {
        if (webSocket != null) {
            // Close with normal closure status code
            webSocket.close(1000, "Closing connection");
            webSocket = null;
        }
        
        isConnected = false;
        reconnectAttempts = 0;
    }
    
    /**
     * Schedule reconnect
     */
    private void scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached");
            return;
        }
        
        reconnectAttempts++;
        
        handler.postDelayed(() -> {
            Log.d(TAG, "Attempting to reconnect... (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
            connect();
        }, RECONNECT_DELAY);
    }
    
    /**
     * Send message to WebSocket server
     * @param message Message to send
     * @return true if message was sent, false otherwise
     */
    public boolean sendMessage(String message) {
        if (!isConnected || webSocket == null) {
            Log.e(TAG, "Cannot send message: WebSocket not connected");
            return false;
        }
        
        return webSocket.send(message);
    }
    
    /**
     * Add WebSocket event listener
     * @param listener WebSocket event listener
     */
    public void addListener(WebSocketEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove WebSocket event listener
     * @param listener WebSocket event listener
     */
    public void removeListener(WebSocketEventListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * WebSocket listener
     */
    private class BearWebSocketListener extends WebSocketListener {
        
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket connected");
            isConnected = true;
            reconnectAttempts = 0;
            
            // Notify listeners
            handler.post(() -> {
                for (WebSocketEventListener listener : listeners) {
                    listener.onConnected();
                }
            });
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "WebSocket message received: " + text);
            
            try {
                // Parse JSON message
                JSONObject json = new JSONObject(text);
                String type = json.getString("type");
                
                if ("patch_update".equals(type)) {
                    // Parse patch updates
                    JSONArray patchesJson = json.getJSONArray("patches");
                    List<Patch> patches = parsePatches(patchesJson);
                    
                    // Notify listeners
                    handler.post(() -> {
                        for (WebSocketEventListener listener : listeners) {
                            listener.onPatchUpdate(patches);
                        }
                    });
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing WebSocket message: " + e.getMessage());
            }
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + reason);
            isConnected = false;
            
            // Notify listeners
            handler.post(() -> {
                for (WebSocketEventListener listener : listeners) {
                    listener.onDisconnected(reason);
                }
            });
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure: " + t.getMessage());
            isConnected = false;
            
            // Notify listeners
            handler.post(() -> {
                for (WebSocketEventListener listener : listeners) {
                    listener.onError(t.getMessage());
                }
            });
            
            // Schedule reconnect
            scheduleReconnect();
        }
    }
    
    /**
     * Parse patches from JSON array
     * @param patchesJson JSON array of patches
     * @return List of patches
     * @throws JSONException If JSON parsing fails
     */
    private List<Patch> parsePatches(JSONArray patchesJson) throws JSONException {
        List<Patch> patches = new ArrayList<>();
        
        for (int i = 0; i < patchesJson.length(); i++) {
            JSONObject patchJson = patchesJson.getJSONObject(i);
            
            String id = patchJson.getString("id");
            String name = patchJson.getString("name");
            String description = patchJson.getString("description");
            String gameVersion = patchJson.getString("game_version");
            String updateDate = patchJson.getString("update_date");
            String status = patchJson.getString("status");
            
            Patch.PatchStatus patchStatus;
            switch (status) {
                case "up_to_date":
                    patchStatus = Patch.PatchStatus.UP_TO_DATE;
                    break;
                case "update_available":
                    patchStatus = Patch.PatchStatus.UPDATE_AVAILABLE;
                    break;
                default:
                    patchStatus = Patch.PatchStatus.NOT_INSTALLED;
                    break;
            }
            
            patches.add(new Patch(id, name, description, gameVersion, updateDate, patchStatus));
        }
        
        return patches;
    }
    
    /**
     * WebSocket event listener interface
     */
    public interface WebSocketEventListener {
        void onConnected();
        void onDisconnected(String reason);
        void onError(String error);
        void onPatchUpdate(List<Patch> patches);
    }
}
