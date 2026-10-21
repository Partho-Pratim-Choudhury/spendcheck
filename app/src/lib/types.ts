/** API types — mirror the SpendCheck backend contract (/api/v1). */

export type CategoryKind = 'FIXED' | 'NEED' | 'WANT';
export type NeedOrWant = 'NEED' | 'WANT';
export type TransactionSource = 'TEXT' | 'VOICE' | 'MANUAL';

/** RFC 7807 ProblemDetail as returned by the backend on errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

export interface User {
  id: number | string;
  email: string;
  name: string;
  /** Day of month salary is credited (1–31); defines the budget cycle. */
  salaryDay: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  /** ISO-8601 timestamp. */
  expiresAt: string;
  user: User;
}

export interface Category {
  id: number | string;
  name: string;
  kind: CategoryKind;
  aliases: string[];
  archived: boolean;
}

export interface Transaction {
  id: string;
  amountPaise: number;
  categoryId: Category['id'];
  categoryName: string;
  merchant: string | null;
  note: string | null;
  /** ISO-8601 timestamp. */
  spentAt: string;
  needOrWant: NeedOrWant | null;
  source: TransactionSource;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionQuery {
  from?: string;
  to?: string;
  categoryId?: Category['id'];
  needOrWant?: NeedOrWant;
}

/** POST /transactions — idempotent on client-generated `id` (UUID). */
export interface CreateTransactionRequest {
  id: string;
  amountPaise: number;
  categoryId: Category['id'];
  merchant?: string;
  note?: string;
  spentAt: string;
  needOrWant?: NeedOrWant;
  source: TransactionSource;
}
