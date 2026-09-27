-- Every service type now uses the same 30-minute slot window - see docs/SEED-DATA.md § Time
-- slots. Previously each service type generated its own start-time grid from its own duration
-- (15/20/30 min), so different service types at the same branch had different, unaligned
-- bookable times; one shared duration keeps the demo's slot grid simple and predictable.
UPDATE service_type SET duration_minutes = 30;
