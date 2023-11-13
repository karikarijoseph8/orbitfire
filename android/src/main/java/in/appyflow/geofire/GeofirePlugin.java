package in.appyflow.geofire;

import android.util.Log;


import androidx.annotation.NonNull;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.firebase.geofire.LocationCallback;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;


import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * GeofirePlugin
 */
public class GeofirePlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

    GeoFire geoFire;
    DatabaseReference databaseReference;
    static MethodChannel channel;
    static EventChannel eventChannel;
    private EventChannel.EventSink events;

    /**
     * Plugin registration.
     */

    public static void pluginInit(BinaryMessenger messenger) {
        GeofirePlugin geofirePlugin = new GeofirePlugin();

        channel = new MethodChannel(messenger, "geofire");
        channel.setMethodCallHandler(geofirePlugin);

        eventChannel = new EventChannel(messenger, "geofireStream");
        eventChannel.setStreamHandler(geofirePlugin);

    }

//    public static void registerWith(Registrar registrar) {
//        pluginInit(registrar.messenger());
//    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {

        Log.i("TAG", call.method.toString());

        if (call.method.equals("GeoFire.start")) {

            databaseReference = FirebaseDatabase.getInstance().getReference(call.argument("path").toString());
            geoFire = new GeoFire(databaseReference);

            if (geoFire.getDatabaseReference() != null) {
                result.success(true);
            } else
                result.success(false);
        } else if (call.method.equals("setLocation")) {
            String serviceType = call.argument("serviceType").toString();
            final String id = call.argument("id").toString();
            final HashMap<String, Object> map = new HashMap<>();
            map.put("serviceType", serviceType);
            geoFire.setLocation(id, new GeoLocation(Double.parseDouble(call.argument("lat").toString()), Double.parseDouble(call.argument("lng").toString())), new GeoFire.CompletionListener() {
                @Override
                public void onComplete(String key, DatabaseError error) {

                    if (error != null) {
                        result.success(false);
                    } else {
                        databaseReference.child(id).updateChildren(map);
                        result.success(true);
                    }

                }
            });


        } else if (call.method.equals("removeLocation")) {

            geoFire.removeLocation(call.argument("id").toString(), new GeoFire.CompletionListener() {
                @Override
                public void onComplete(String key, DatabaseError error) {

                    if (error != null) {
                        result.success(false);
                    } else {
                        result.success(true);
                    }

                }
            });


        } else if (call.method.equals("getLocation")) {
            final String id = call.argument("id").toString();
            geoFire.getLocation(id, new LocationCallback() {
                @Override
                public void onLocationResult(String key, GeoLocation location) {
                    final GeoLocation mLocation = location;
                    final HashMap<String, Object> map = new HashMap<>();

                    if (mLocation != null) {
                        Log.d("TAG", "onLocationResult: map lat" + location.latitude + " long" + location.longitude);
                        databaseReference.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                                if (dataSnapshot.exists()) {
                                    String serviceType = dataSnapshot.child("serviceType").getValue(String.class);

                                    map.put("lat", mLocation.latitude);
                                    map.put("lng", mLocation.longitude);
                                    map.put("error", null);
                                    map.put("serviceType", serviceType);
                                    Log.d("TAG", "onLocationResult: map serviceType" + serviceType);
                                } else {
                                    map.put("error", String.format("There is no location for key in GeoFire"));
                                }
                                result.success(map);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                map.put("error", String.format("There is no location for key serviceType in GeoFire"));
                                result.success(map);
                            }
                        });
                    } else {
                        map.put("error", String.format("There is no location for key %s in GeoFire", key));
                        result.success(map);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("error", "There was an error getting the GeoFire location: " + databaseError);

                    result.success(map);
                }
            });


        } else if (call.method.equals("queryAtLocation")) {
            geoFireArea(Double.parseDouble(call.argument("lat").toString()), Double.parseDouble(call.argument("lng").toString()), result, Double.parseDouble(call.argument("radius").toString()));
        } else if (call.method.equals("stopListener")) {

            if (geoQuery != null) {
                geoQuery.removeAllListeners();
            }

            result.success(true);
        } else {
            result.notImplemented();
        }
    }

    GeoQuery geoQuery;

    HashMap<String, Object> hashMap = new HashMap<>();


    private void geoFireArea(final double latitude, double longitude, final Result result, double radius) {
        try {

            final ArrayList<String> arrayListKeys = new ArrayList<>();

            if (geoQuery != null) {
                geoQuery.setLocation(new GeoLocation(latitude, longitude), radius);
            } else {
                geoQuery = geoFire.queryAtLocation(new GeoLocation(latitude, longitude), radius);
            }

            geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                @Override
                public void onKeyEntered(String key, GeoLocation location) {
                    final String mKey = key;
                    final GeoLocation mLocation = location;
                    if (events != null) {
                        databaseReference.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                                if (dataSnapshot.exists()) {
                                    String serviceType = dataSnapshot.child("serviceType").getValue(String.class);
                                    hashMap.clear();
                                    hashMap.put("callBack", "onKeyEntered");
                                    hashMap.put("key", mKey);
                                    hashMap.put("latitude", mLocation.latitude);
                                    hashMap.put("longitude", mLocation.longitude);
                                    hashMap.put("serviceType", serviceType);
                                    Log.d("TAG", "onLocationResult: map serviceType" + serviceType);
                                } else {
                                    hashMap.put("error", String.format("There is no location for key in GeoFire"));
                                }
                                events.success(hashMap);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                geoQuery.removeAllListeners();
                                arrayListKeys.add(mKey);
                            }
                        });
                    } else {
                        geoQuery.removeAllListeners();
                        arrayListKeys.add(key);
                    }

                }

                @Override
                public void onKeyExited(String key) {
                    arrayListKeys.remove(key);

                    if (events != null) {

                        hashMap.clear();
                        hashMap.put("callBack", "onKeyExited");
                        hashMap.put("key", key);
                        events.success(hashMap);
                    } else {
                        geoQuery.removeAllListeners();
                    }

                }

                @Override
                public void onKeyMoved(String key, GeoLocation location) {
                    final String mKey = key;
                    final GeoLocation mLocation = location;
                    if (events != null) {
                        hashMap.clear();
                        databaseReference.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                                if (dataSnapshot.exists()) {
                                    String serviceType = dataSnapshot.child("serviceType").getValue(String.class);
                                    hashMap.clear();
                                    hashMap.put("callBack", "onKeyMoved");
                                    hashMap.put("key", mKey);
                                    hashMap.put("latitude", mLocation.latitude);
                                    hashMap.put("longitude", mLocation.longitude);
                                    hashMap.put("serviceType", serviceType);
                                    Log.d("TAG", "onLocationResult: map serviceType" + serviceType);
                                } else {
                                    hashMap.put("error", String.format("There is no location for key in GeoFire"));
                                }
                                events.success(hashMap);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                geoQuery.removeAllListeners();
                                arrayListKeys.add(mKey);
                            }
                        });
                    } else {
                        geoQuery.removeAllListeners();
                    }

                }

                @Override
                public void onGeoQueryReady() {
//                    geoQuery.removeAllListeners();
//                    result.success(arrayListKeys);

                    if (events != null) {
                        hashMap.clear();

                        hashMap.put("callBack", "onGeoQueryReady");
                        hashMap.put("result", arrayListKeys);

                        events.success(hashMap);

                    } else {
                        geoQuery.removeAllListeners();
                    }

                }

                @Override
                public void onGeoQueryError(DatabaseError error) {

                    if (events != null) {

                        events.error("Error ", "GeoQueryError", error);
                    } else {
                        geoQuery.removeAllListeners();
                    }


                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            result.error("Error ", "General Error", e);
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        events = eventSink;
    }

    @Override
    public void onCancel(Object o) {

        geoQuery.removeAllListeners();
        events = null;

    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        pluginInit(binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
    }
}