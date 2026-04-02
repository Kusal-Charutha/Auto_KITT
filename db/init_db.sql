-- Create the main sensor data table
CREATE TABLE IF NOT EXISTS sensor_data (
    time TIMESTAMPTZ NOT NULL,
    user_id TEXT,
    user_name TEXT,
    device_id TEXT NOT NULL,
    session_id BIGINT,
    date_str TEXT,
    engine_rpm DOUBLE PRECISION,
    vehicle_speed DOUBLE PRECISION,
    throttle DOUBLE PRECISION,
    engine_load DOUBLE PRECISION,
    coolant_temp DOUBLE PRECISION,
    intake_temp DOUBLE PRECISION,
    maf DOUBLE PRECISION,
    fuel_rate DOUBLE PRECISION,
    run_time DOUBLE PRECISION,
    ltft1 DOUBLE PRECISION,
    stft1 DOUBLE PRECISION,
    map DOUBLE PRECISION,
    fuel_level DOUBLE PRECISION,
    abs_throttle_b DOUBLE PRECISION,
    pedal_d DOUBLE PRECISION,
    pedal_e DOUBLE PRECISION,
    cmd_throttle DOUBLE PRECISION,
    equiv_ratio DOUBLE PRECISION,
    baro DOUBLE PRECISION,
    rel_throttle DOUBLE PRECISION,
    timing DOUBLE PRECISION,
    cat_b1s1 DOUBLE PRECISION,
    cat_b1s2 DOUBLE PRECISION,
    voltage DOUBLE PRECISION,
    evap_purge DOUBLE PRECISION
);

-- Convert to hypertable partitioned by time for TimescaleDB optimization
SELECT create_hypertable('sensor_data', 'time', if_not_exists => TRUE);

-- Create an index on device_id and session_id for faster historical lookups
CREATE INDEX IF NOT EXISTS ix_device_session_time ON sensor_data (device_id, session_id, time DESC);

