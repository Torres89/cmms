/**
 * The machine dossier — everything true about one machine right now.
 *
 * Mirrors AssetDossierDTO on the API. Retrieval answers "what does the manual
 * say"; this answers "what is true about this machine", which is the question
 * the dossier page is built around.
 */

export interface MeterReadingSummary {
  meterId: number;
  name: string;
  unit?: string;
  lastValue?: number;
  lastReadingAt?: string;
  updateFrequency: number;
  overdue: boolean;
}

export interface SpecSummary {
  id: number;
  specGroup: string;
  specKey: string;
  label: string;
  value: string;
  unit?: string;
  source?: string;
  verified: boolean;
  sourceDocumentTitle?: string;
  sourcePage?: number;
  confidence?: number;
}

export interface ComponentSummary {
  id: number;
  serialNumber: string;
  name?: string;
  /**
   * The position asset this component occupies. Matching on positionName
   * instead cannot tell two positions called "Bearing" apart, and moves a
   * component silently when a position is renamed.
   */
  positionId?: number;
  positionCode?: string;
  positionName?: string;
  status?: string;
  totalHours?: number;
  hoursSinceOverhaul?: number;
  hourLimit?: number;
  totalCycles?: number;
  cycleLimit?: number;
  calendarLimitMonths?: number;
  /** 0..1, or absent when the component is not life-limited. */
  remainingLifeFraction?: number;
  installedAt?: string;
}

export interface StructureNode {
  id: number;
  name: string;
  positionCode?: string;
  level?: string;
  trackingClass?: string;
  criticality?: number;
  children: StructureNode[];
}

export interface PmSummary {
  id: number;
  title?: string;
  triggerMode?: string;
  percent?: number;
  due: boolean;
  warning: boolean;
  drivingCounter?: string;
  remaining?: number;
  remainingUnit?: string;
  nextDueDate?: string;
}

export interface WorkOrderSummary {
  id: number;
  title?: string;
  status?: string;
  priority?: string;
  dueDate?: string;
  assignedTo?: string;
}

export interface FailureSummary {
  id: number;
  code?: string;
  name?: string;
  occurredAt?: string;
  downtimeMinutes?: number;
  severity?: number;
  cause?: string;
  correctiveAction?: string;
}

export interface DocumentSummary {
  id: number;
  title: string;
  docType?: string;
  revision?: string;
  pageCount?: number;
  ingestStatus?: string;
  chunkCount?: number;
}

export interface SpecCompleteness {
  captured: number;
  expected: number;
  requiredCaptured: number;
  requiredExpected: number;
  verified: number;
  pendingVerification: number;
  percent: number;
  complete: boolean;
  missingKeys: {
    specKey: string;
    specGroup: string;
    label?: string;
    unit?: string;
    required: boolean;
  }[];
}

export interface AssetDossier {
  id: number;
  name: string;
  customId?: string;
  model?: string;
  manufacturer?: string;
  serialNumber?: string;
  equipmentClass?: string;
  level?: string;
  status?: string;
  locationPath?: string;
  area?: string;
  inServiceDate?: string;
  warrantyExpirationDate?: string;
  criticality?: number;
  downtimeCostPerHour?: number;
  replacementCost?: number;
  description?: string;
  functionalDescription?: string;

  meters: MeterReadingSummary[];
  keySpecs: SpecSummary[];
  specCompleteness?: SpecCompleteness;
  components: ComponentSummary[];
  structure: StructureNode[];
  upcomingMaintenance: PmSummary[];
  openWorkOrders: WorkOrderSummary[];
  recentFailures: FailureSummary[];
  documents: DocumentSummary[];

  /** The rendered card AI clients read. */
  text?: string;
}

export interface AssetSpec {
  id: number;
  specGroup: string;
  specKey: string;
  label?: string;
  valueText?: string;
  valueNum?: number;
  unit?: string;
  source?: string;
  sourcePage?: number;
  confidence?: number;
  verified: boolean;
  needsVerification: boolean;
  sourceDocument?: { id: number; title: string };
  verifiedAt?: string;
  verifiedBy?: {
    id: number;
    firstName?: string;
    lastName?: string;
  };
}

export type SpecValueType = 'TEXT' | 'NUM' | 'BOOL' | 'DATE';

export interface SpecKeyCatalogEntry {
  id: number;
  equipmentClass: string;
  specGroup: string;
  specKey: string;
  labelEn: string;
  labelEs?: string;
  unit?: string;
  valueType: SpecValueType;
  required: boolean;
  displayOrder?: number;
  systemSeeded: boolean;
}

/**
 * A serialized component as the components endpoints return it, as opposed to
 * the flattened ComponentSummary the dossier carries.
 */
export interface ComponentInstance {
  id: number;
  serialNumber: string;
  manufacturer?: string;
  mpn?: string;
  status?: string;
  partType?: { id: number; name?: string };
  currentPosition?: { id: number; name?: string; positionCode?: string };
  manufactureDate?: string;
  acquiredAt?: string;
  acquisitionCost?: number;
  totalHours?: number;
  totalCycles?: number;
  hoursSinceOverhaul?: number;
  cyclesSinceOverhaul?: number;
  hourLimit?: number;
  cycleLimit?: number;
  calendarLimitMonths?: number;
  notes?: string;
}

/** A catalogued way this class of machine breaks. */
export interface FailureMode {
  id: number;
  equipmentClass?: string;
  subunit?: string;
  code: string;
  nameEn: string;
  nameEs?: string;
  description?: string;
  typicalMechanism?: string;
  typicalCauses?: string;
  detectionMethods?: string;
  severityDefault?: number;
  systemSeeded?: boolean;
}

export const detectionStages = [
  'OPERATOR',
  'PM_INSPECTION',
  'CONDITION_MONITORING',
  'BREAKDOWN'
] as const;
export type DetectionStage = typeof detectionStages[number];

/** One occurrence: what broke on this machine, this time. */
export interface FailureEvent {
  id: number;
  occurredAt?: string;
  createdAt?: string;
  failureMode?: FailureMode;
  component?: { id: number; serialNumber?: string };
  workOrder?: { id: number; title?: string };
  mechanism?: string;
  cause?: string;
  detectionMethod?: string;
  detectedAt?: DetectionStage;
  severity?: number;
  downtimeMinutes?: number;
  repairCost?: number;
  correctiveAction?: string;
  preventiveRecommendation?: string;
}

/** The body every install / remove / overhaul / scrap POST takes. */
export interface ComponentAction {
  positionAssetId?: number;
  occurredAt?: string;
  meterValue?: number;
  workOrderId?: number;
  vendorId?: number;
  cost?: number;
  reason?: string;
}

export interface BomLine {
  id: number;
  positionCode?: string;
  qtyPerAssembly?: number;
  consumable: boolean;
  replaceIntervalHours?: number;
  replaceIntervalMonths?: number;
  notes?: string;
  part?: {
    id: number;
    name: string;
    mpn?: string;
    manufacturer?: string;
    quantity: number;
    unit?: string;
    /** An order-on-demand part: zero on hand is normal, not a shortage. */
    nonStock?: boolean;
    minQuantity?: number;
    image?: { id: number; url?: string };
  };
}

export interface RestockKit {
  assetId: number;
  assetName: string;
  horizonDays: number;
  hoursPerDay: number;
  estimatedTotal: number;
  note?: string;
  lines: {
    partId: number;
    name: string;
    mpn?: string;
    positionCode?: string;
    unit?: string;
    quantity: number;
    onHand: number;
    shortfall: number;
    daysUntilDue?: number;
    supplierName?: string;
    unitPrice?: number;
    currency?: string;
    leadTimeDays?: number;
    productUrl?: string;
    urgent: boolean;
  }[];
}
