import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { ApiError, apiFetch } from './api';
import { addDays, toYmd } from './dates';
import type {
  Category,
  CreateCategoryRequest,
  CreateTransactionFromInputRequest,
  DailySummary,
  Dashboard,
  DecisionRequest,
  DecisionResponse,
  Goal,
  GoalInput,
  InputResult,
  MonthlyReport,
  Plan,
  PlanSummary,
  PutPlanRequest,
  Transaction,
  TransactionQuery,
  UpdateTransactionRequest,
  User,
} from './types';

export const queryKeys = {
  categories: ['categories'] as const,
  planCurrent: ['plans', 'current'] as const,
  plans: ['plans', 'list'] as const,
  dashboard: ['dashboard'] as const,
  goals: ['goals'] as const,
  transactionsAll: ['transactions'] as const,
  transactions: (q: TransactionQuery) => ['transactions', q] as const,
  reportsAll: ['reports'] as const,
  report: (planId: number | null) => ['reports', planId ?? 'latest'] as const,
  summaryAll: ['summary'] as const,
  summary: (date: string | null) => ['summary', date ?? 'today'] as const,
};

/** Resolve to null instead of throwing on 404 (no plan yet). */
async function orNullOn404<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** Everything derived from spends: dashboard, lists, reports, daily summary. */
export function invalidateSpendData(qc: QueryClient) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: queryKeys.dashboard }),
    qc.invalidateQueries({ queryKey: queryKeys.transactionsAll }),
    qc.invalidateQueries({ queryKey: queryKeys.reportsAll }),
    qc.invalidateQueries({ queryKey: queryKeys.summaryAll }),
  ]);
}

function invalidatePlanData(qc: QueryClient) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ['plans'] }),
    qc.invalidateQueries({ queryKey: queryKeys.goals }),
    invalidateSpendData(qc),
  ]);
}

// --- queries -----------------------------------------------------------------

export const useCategories = () =>
  useQuery({ queryKey: queryKeys.categories, queryFn: ({ signal }) => apiFetch<Category[]>('/categories', { signal }) });

export const useCurrentPlan = () =>
  useQuery({
    queryKey: queryKeys.planCurrent,
    queryFn: ({ signal }) => orNullOn404(apiFetch<Plan>('/plans/current', { signal })),
  });

export const usePlans = () =>
  useQuery({ queryKey: queryKeys.plans, queryFn: ({ signal }) => apiFetch<PlanSummary[]>('/plans', { signal }) });

export const useDashboard = () =>
  useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: ({ signal }) => orNullOn404(apiFetch<Dashboard>('/dashboard', { signal })),
  });

export const useGoals = () =>
  useQuery({ queryKey: queryKeys.goals, queryFn: ({ signal }) => apiFetch<Goal[]>('/goals', { signal }) });

export function useTransactions(q: TransactionQuery, enabled = true) {
  return useQuery({
    queryKey: queryKeys.transactions(q),
    queryFn: ({ signal }) =>
      apiFetch<Transaction[]>('/transactions', {
        signal,
        query: { from: q.from, to: q.to, categoryId: q.categoryId, needOrWant: q.needOrWant },
      }),
    enabled,
  });
}

export function useTodayTransactions() {
  const today = toYmd(new Date());
  return useTransactions({ from: today, to: today });
}

export const useReport = (planId: number | null, enabled = true) =>
  useQuery({
    queryKey: queryKeys.report(planId),
    enabled,
    queryFn: ({ signal }) =>
      orNullOn404(apiFetch<MonthlyReport>('/reports/monthly', { signal, query: { planId: planId ?? undefined } })),
  });

export const useDailySummary = (date: string | null = null) =>
  useQuery({
    queryKey: queryKeys.summary(date),
    queryFn: ({ signal }) =>
      orNullOn404(apiFetch<DailySummary>('/summary/daily', { signal, query: { date: date ?? undefined } })),
  });

/** Default spends range: the current plan period, else the last 30 days. */
export function useSpendsRange(): { from: string; to: string; ready: boolean; fromPlan: boolean } {
  const plan = useCurrentPlan();
  const today = toYmd(new Date());
  if (plan.data) return { from: plan.data.periodStart, to: plan.data.periodEnd, ready: true, fromPlan: true };
  return { from: addDays(today, -29), to: today, ready: !plan.isPending, fromPlan: false };
}

// --- mutations ---------------------------------------------------------------

export function useCreateTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTransactionFromInputRequest) =>
      apiFetch<Transaction>('/transactions', { method: 'POST', body }),
    onSettled: () => invalidateSpendData(qc),
  });
}

export function useUpdateTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTransactionRequest }) =>
      apiFetch<Transaction>(`/transactions/${id}`, { method: 'PATCH', body }),
    onSettled: () => invalidateSpendData(qc),
  });
}

export function useDeleteTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/transactions/${id}`, { method: 'DELETE' }),
    onSettled: () => invalidateSpendData(qc),
  });
}

export function useDecision() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ checkId, body }: { checkId: number; body: DecisionRequest }) =>
      apiFetch<DecisionResponse>(`/checks/${checkId}/decision`, { method: 'POST', body }),
    onSettled: () => invalidateSpendData(qc),
  });
}

export const sendTextInput = (text: string) =>
  apiFetch<InputResult>('/inputs/text', { method: 'POST', body: { text } });

export function sendVoiceInput(uri: string) {
  const form = new FormData();
  // React Native's FormData accepts a { uri, name, type } file descriptor.
  form.append('audio', { uri, name: 'voice.m4a', type: 'audio/mp4' } as unknown as Blob);
  return apiFetch<InputResult>('/inputs/voice', { method: 'POST', body: form, timeoutMs: 30_000 });
}

export function useSavePlan() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: PutPlanRequest) => apiFetch<Plan>('/plans/current', { method: 'PUT', body }),
    onSuccess: (plan) => qc.setQueryData(queryKeys.planCurrent, plan),
    onSettled: () => invalidatePlanData(qc),
  });
}

export function useRollover() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<Plan>('/plans/rollover', { method: 'POST' }),
    onSuccess: (plan) => qc.setQueryData(queryKeys.planCurrent, plan),
    onSettled: () => invalidatePlanData(qc),
  });
}

export function useSetSalaryDay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (salaryDay: number) =>
      apiFetch<User>('/users/me/salary-day', { method: 'PUT', body: { salaryDay } }),
    onSettled: () => qc.invalidateQueries({ queryKey: ['plans'] }),
  });
}

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateCategoryRequest) => apiFetch<Category>('/categories', { method: 'POST', body }),
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.categories }),
  });
}

export function useSaveGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number | null; body: Partial<GoalInput> }) =>
      id === null
        ? apiFetch<Goal>('/goals', { method: 'POST', body })
        : apiFetch<Goal>(`/goals/${id}`, { method: 'PATCH', body }),
    onSettled: () =>
      Promise.all([qc.invalidateQueries({ queryKey: queryKeys.goals }), qc.invalidateQueries({ queryKey: queryKeys.dashboard })]),
  });
}

export function useDeleteGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/goals/${id}`, { method: 'DELETE' }),
    onSettled: () =>
      Promise.all([qc.invalidateQueries({ queryKey: queryKeys.goals }), qc.invalidateQueries({ queryKey: queryKeys.dashboard })]),
  });
}
