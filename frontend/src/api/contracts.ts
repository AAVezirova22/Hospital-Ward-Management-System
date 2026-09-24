export interface Entity {
  id: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  nextPage: number | null;
}
export interface Patient extends Entity {
  patientIdentifier: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  address: string | null;
  phoneNumber: string | null;
}
export interface PatientDirectoryItem {
  id: number;
  patientIdentifier: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
}
export interface PatientDirectoryPage {
  items: PatientDirectoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  nextPage: number | null;
}
export interface Doctor extends Entity {
  doctorIdentifier: string;
  firstName: string;
  lastName: string;
  specialty: string;
  active: boolean;
}
export interface RoomCapacity extends Entity {
  roomNumber: string;
  bedCount: number;
  active: boolean;
  capabilities: string[];
  occupiedBeds: number;
  heldBeds: number;
  activeHeldBeds: number;
  availableBeds: number;
  holds: BedHold[];
}
export interface BedHold extends Entity {
  roomId: number;
  bedCount: number;
  reason: string;
  startsAt: string;
  endsAt: string;
  createdBy: number;
  cancelledAt: string | null;
}
export interface Admission extends Entity {
  admissionNumber: string;
  patientId: number;
  attendingDoctorId: number;
  admissionDateTime: string;
  dischargeDateTime: string | null;
  expectedDischargeDate: string | null;
  requiredRoomCapabilities: string[];
  status: "ACTIVE" | "DISCHARGED" | "CANCELLED";
}
export interface RoomAssignment extends Entity {
  admissionId: number;
  roomId: number;
  assignedAt: string;
  releasedAt: string | null;
  reason: string;
}
export interface MedicalProcedure extends Entity {
  procedureCode: string;
  procedureName: string;
  currentCost: number;
  active: boolean;
}
export interface ProcedureRecord extends Entity {
  admissionId: number;
  admissionNumber?: string;
  medicalProcedureId: number;
  performedByDoctorId: number;
  performedAt: string;
  note: string | null;
  priceAtExecution: number;
}
export interface ProcedureView {
  record: ProcedureRecord;
  procedure: MedicalProcedure;
  doctor: Doctor;
  patient?: Patient;
}
export interface AdmissionView {
  admission: Admission;
  patient: Patient;
  doctor: Doctor;
  assignment: RoomAssignment | null;
  rooms: { assignment: RoomAssignment; room: RoomCapacity }[];
  procedures: ProcedureView[];
  totalCost: number;
}
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  nextPage: number | null;
}
export interface DashboardReport {
  activeAdmissions: number;
  occupiedBeds: number;
  heldBeds: number;
  totalBeds: number;
  availableBeds: number;
  activeDoctors: number;
  proceduresToday: number;
  scope: string;
  timeZone: string;
}
export interface OperationalActivity {
  id: number;
  eventType: string;
  timestamp: string;
  admissionId: number;
  source: string;
}
export interface OverdueDischarge {
  admissionId: number;
  admissionNumber: string;
  patientId: number;
  patientIdentifier: string;
  patientName: string;
  attendingDoctorId: number;
  attendingDoctorName: string;
  expectedDischargeDate: string;
  daysOverdue: number;
}
export interface OperationsReport {
  trends: {
    date: string;
    admissions: number;
    discharges: number;
    occupied: number;
  }[];
  activity: OperationalActivity[];
  averageStayDays: number;
  longStayPatients: number;
  expectedDischargesToday: number;
  overdueDischarges: OverdueDischarge[];
  thresholds: {
    longStayDays: number;
    warningPercent: number;
    criticalPercent: number;
  };
  scope: string;
  timeZone: string;
  asOf: string;
}
export interface DischargeReminderOutcome {
  id: number;
  expectedDischargeDate: string;
  windowDays: number;
  status:
    | "PENDING"
    | "SENDING"
    | "ACCEPTED"
    | "FAILED"
    | "NO_RECIPIENT"
    | "CANCELLED";
  recipientCount: number;
  attemptCount: number;
  lastAttemptAt: string | null;
  providerMessageId: string | null;
  errorCode: string | null;
  createdAt: string;
}
export interface ArrivalPlan {
  arrivals: number;
  placements: { arrival: number; roomId: number; roomNumber: string }[];
  unplaced: number;
}
export interface ProcedureReport {
  rows: ProcedureView[];
  totalCost: number;
  byDoctor: Record<string, number>;
  from: string;
  to: string;
  timeZone: string;
}
export interface DoctorWorkloadDoctor {
  id: number;
  version: number;
  doctorIdentifier: string;
  firstName: string;
  lastName: string;
  specialty: string;
  active: boolean;
}
export interface DoctorWorkloadRow {
  doctor: DoctorWorkloadDoctor;
  activeAdmissions: number;
  assignedBeds: number;
  recentProcedures: number;
}
export interface DoctorWorkloadReport {
  rows: DoctorWorkloadRow[];
  from: string;
  to: string;
  timeZone: string;
  scope: "Department" | "Your workload";
}
export interface WorkspaceDepartment {
  id: number;
  name: string;
  role: string;
  hasJoinCode: boolean;
  timeZone: string;
}
export interface WorkspaceHospital {
  id: number;
  name: string;
  owner: boolean;
  hasJoinCode: boolean;
  departments: WorkspaceDepartment[];
}
export interface WorkspaceList {
  activeDepartmentId: number;
  timeZone: string;
  hospitals: WorkspaceHospital[];
}
