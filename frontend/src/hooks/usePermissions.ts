import { useAuth } from '../context/AuthContext';

/** Role-based permission map for the ApexCare frontend. */
const PERMISSIONS = {
  // Patient management
  canViewPatients:         ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST'],
  canRegisterPatient:     ['ADMIN', 'RECEPTIONIST'],
  canEditPatient:         ['ADMIN', 'RECEPTIONIST', 'DOCTOR'],
  canDeletePatient:       ['ADMIN'],
  canAddVitals:           ['ADMIN', 'DOCTOR', 'NURSE'],
  canCreateReferral:      ['ADMIN', 'DOCTOR'],

  // Appointments
  canViewAllAppointments: ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN'],
  canScheduleAppointment: ['ADMIN', 'RECEPTIONIST', 'PATIENT'],
  canCompleteAppointment: ['ADMIN', 'DOCTOR', 'NURSE'],
  canCancelAppointment:   ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT'],
  canRescheduleAppointment: ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT'],

  // Clinical & Operations
  canViewPharmacy:        ['ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST'],
  canAddMedicine:         ['ADMIN', 'DOCTOR', 'PHARMACIST'],
  canDispenseMedication:  ['ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST'],
  canRestockPharmacy:     ['ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST'],
  canViewLabs:            ['ADMIN', 'DOCTOR', 'NURSE', 'LAB_TECHNICIAN', 'RECEPTIONIST'],
  canOrderLabTest:        ['ADMIN', 'DOCTOR', 'NURSE'],
  canUploadLabResult:     ['ADMIN', 'DOCTOR', 'NURSE', 'LAB_TECHNICIAN'],
  canViewWards:           ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN'],
  canAdmitBed:            ['ADMIN', 'DOCTOR', 'RECEPTIONIST'],
  canDischargeBed:        ['ADMIN', 'DOCTOR'],
  canMarkBedReady:        ['ADMIN', 'NURSE', 'DOCTOR', 'RECEPTIONIST'],

  // Billing
  canViewBilling:         ['ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT'],
  canViewAllInvoices:     ['ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST'],
  canCreateInvoice:       ['ADMIN', 'BILLING_ADMIN'],
  canProcessPayment:      ['ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT'],
  canManageInsurance:     ['ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST'],

  // Administration
  canManageUsers:         ['ADMIN'],
  canViewAuditLogs:       ['ADMIN'],
} as const;

export function usePermissions() {
  const { user } = useAuth();
  const role = user?.role || '';

  const can = (permission: keyof typeof PERMISSIONS): boolean => {
    return (PERMISSIONS[permission] as readonly string[])?.includes(role) ?? false;
  };

  const isPatient = role === 'PATIENT';
  const isDoctor = role === 'DOCTOR';
  const isNurse = role === 'NURSE';
  const isReceptionist = role === 'RECEPTIONIST';
  const isBillingAdmin = role === 'BILLING_ADMIN';
  const isAdmin = role === 'ADMIN';
  const isStaff = ['ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN'].includes(role);
  const isClinical = ['ADMIN', 'DOCTOR', 'NURSE'].includes(role);

  return {
    can,
    role,
    isPatient,
    isDoctor,
    isNurse,
    isReceptionist,
    isBillingAdmin,
    isAdmin,
    isStaff,
    isClinical,
  };
}
