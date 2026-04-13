import numpy as np
import json
import tensorflow as tf

# Load the tflite model and allocate tensors.
interpreter = tf.lite.Interpreter(model_path="app/src/main/assets/driver_behavior_alert_model.tflite")
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

with open('app/src/main/assets/feature_columns.json', 'r') as f:
    feature_columns = json.load(f)

with open('app/src/main/assets/scaler_params.json', 'r') as f:
    scaler_params = json.load(f)

# Mock idle data
sensor_data = {
    "rpm_del_motor": 850.0,
    "velocidad_km_h": 0.0,
    "carga_calculada_del_motor": 20.0, # typical idle load
    "temperatura_del_liquido_de_enfriamiento_del_motor": 90.0,
    "voltaje_de_la_bateria": 14.0,
    "presion_absoluta_del_colector_de_admision": 30.0,
    "temperatura_del_aire_del_colector_de_admision": 40.0,
    "posicion_absoluta_del_acelerador": 15.0
}

input_values = np.zeros(len(feature_columns), dtype=np.float32)

print("\n--- Model Feature Diagnostics ---")

for i, feature in enumerate(feature_columns):
    # AutoKITT sets missing features to 0.0 before scaling
    value = sensor_data.get(feature, 0.0) 
    mean = scaler_params['mean'][i]
    scale = scaler_params['scale'][i]
    
    scaled = (value - mean) / scale if scale != 0 else 0
    input_values[i] = scaled
    
    print(f"{feature}: \t raw={value} \t scaled={scaled:.2f} (mean={mean:.2f}, scale={scale:.2f})")

print("\n--- Results ---")
interpreter.set_tensor(input_details[0]['index'], [input_values])
interpreter.invoke()

output_data = interpreter.get_tensor(output_details[0]['index'])
print("Aggressive Probability:", output_data[0][0])
