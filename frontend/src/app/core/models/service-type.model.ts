// Matches docs/api/openapi.yaml - a customer is always exactly one
// of ClientType's two values; ApplicableClientType additionally allows BOTH for the catalog.
export type ClientType = 'NEW_CLIENT' | 'EXISTING_CLIENT';
export type ApplicableClientType = ClientType | 'BOTH';

export interface ServiceType {
  id: string;
  name: string;
  durationMinutes: number;
  applicableClientType: ApplicableClientType;
}
