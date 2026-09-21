export type Entity = {
  id: number;
  version: number;
  createdAt: string;
  updatedAt: string;
};
export type Patient = Entity & {
  patientIdentifier: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  address: string | null;
  phoneNumber: string | null;
};
export type Doctor = Entity & {
  doctorIdentifier: string;
  firstName: string;
  lastName: string;
  specialty: string;
  active: boolean;
};
export type RoomCapacity = Entity & {
  roomNumber: string;
  bedCount: number;
  active: boolean;
  occupiedBeds: number;
  availableBeds: number;
};
export type Admission = Entity & {
  admissionNumber: string;
  patientId: number;
  attendingDoctorId: number;
  admissionDateTime: string;
  dischargeDateTime: string | null;
  expectedDischargeDate: string | null;
  status: "ACTIVE" | "DISCHARGED" | "CANCELLED";
};
export type RoomAssignment = Entity & {
  admissionId: number;
  roomId: number;
  assignedAt: string;
  releasedAt: string | null;
  reason: string;
};
export type MedicalProcedure = Entity & {
  procedureCode: string;
  procedureName: string;
  currentCost: number;
  active: boolean;
};
export type ProcedureRecord = Entity & {
  admissionId: number;
  medicalProcedureId: number;
  performedByDoctorId: number;
  performedAt: string;
  note: string | null;
  priceAtExecution: number;
};
export type ProcedureView = {
  record: ProcedureRecord;
  procedure: MedicalProcedure;
  doctor: Doctor;
  patient?: Patient;
};
export type AdmissionView = {
  admission: Admission;
  patient: Patient;
  doctor: Doctor;
  assignment: RoomAssignment | null;
  rooms: { assignment: RoomAssignment; room: RoomCapacity }[];
  procedures: ProcedureView[];
  totalCost: number;
};
export type DashboardReport = {
  activeAdmissions: number;
  occupiedBeds: number;
  totalBeds: number;
  availableBeds: number;
  activeDoctors: number;
  proceduresToday: number;
  scope: string;
};
export type OperationalActivity = {
  id: number;
  eventType: string;
  timestamp: string;
  admissionId: number;
  source: string;
};
export type OperationsReport = {
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
};
export type ArrivalPlan = {
  arrivals: number;
  placements: { arrival: number; roomId: number; roomNumber: string }[];
  unplaced: number;
};
export type ProcedureReport = {
  rows: ProcedureView[];
  totalCost: number;
  byDoctor: Record<string, number>;
  from: string;
  to: string;
};
export type WorkspaceDepartment = {
  id: number;
  name: string;
  role: string;
  hasJoinCode: boolean;
};
export type WorkspaceHospital = {
  id: number;
  name: string;
  owner: boolean;
  hasJoinCode: boolean;
  departments: WorkspaceDepartment[];
};
export type WorkspaceList = {
  activeDepartmentId: number;
  hospitals: WorkspaceHospital[];
};
export type Account = {
  id: number;
  version: number;
  username: string;
  role: string;
  enabled: boolean;
  doctorId: number | null;
  patientId: number | null;
  email: string | null;
  emailVerified: boolean;
  requestedRole: string | null;
  accountRole: string | null;
  departmentRole: string | null;
};
export type PatientDossier = {
  patient: Patient;
  admissions: AdmissionView[];
};
