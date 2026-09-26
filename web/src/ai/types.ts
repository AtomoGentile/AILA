import type { DeadlineDto, RelevanceBadge } from '@worker/contracts';

// Esito di una classificazione (stessa forma che si salva sul server).
export interface Classification {
  circularNumber: number;
  badge: RelevanceBadge;
  summary: string;
  deadlines: DeadlineDto[];
  isFallback: boolean;
  modelLabel: string;
}
