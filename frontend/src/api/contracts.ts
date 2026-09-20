export interface Entity {
  id: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface Patient extends Entity {
  patientIdentifier: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  address: string | null;
  phoneNumber: string | null;
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
  occupiedBeds: number;
  availableBeds: number;
}
export interface Admission extends Entity {
  admissionNumber: string;
  patientId: number;
  attendingDoctorId: number;
  admissionDateTime: string;
  dischargeDateTime: string | null;
  expectedDischargeDate: string | null;
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
export interface DashboardReport {
  activeAdmissions: number;
  occupiedBeds: number;
  totalBeds: number;
  availableBeds: number;
  activeDoctors: number;
  proceduresToday: number;
  scope: string;
}
export interface OperationalActivity {
  id: number;
  eventType: string;
  timestamp: string;
  admissionId: number;
  source: string;
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
  thresholds: {
    longStayDays: number;
    warningPercent: number;
    criticalPercent: number;
  };
  scope: string;
  asOf: string;
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
}
export interface WorkspaceDepartment {
  id: number;
  name: string;
  role: string;
  joinCode: string | null;
}
export interface WorkspaceHospital {
  id: number;
  name: string;
  owner: boolean;
  joinCode: string | null;
  departments: WorkspaceDepartment[];
}
export interface WorkspaceList {
  activeDepartmentId: number;
  hospitals: WorkspaceHospital[];
}
