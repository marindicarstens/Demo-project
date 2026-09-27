// Matches the AvailabilitySlot schema in docs/api/openapi.yaml.
export interface AvailabilitySlot {
  id: string;
  date: string; // "2026-09-21" - LocalDate
  startTime: string; // "09:00:00" - LocalTime
  remainingCapacity: number;
}
