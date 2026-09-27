// Matches the Branch schema in docs/api/openapi.yaml.
export interface Branch {
  id: string;
  name: string;
  address: string;
  city: string;
  phone?: string;
  opensAt: string; // "08:30:00" - LocalTime, serialized with seconds
  closesAt: string;
}
