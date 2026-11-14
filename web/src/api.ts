export type PaymentStatus = "INITIATED" | "APPROVED" | "FLAGGED" | "ON_HOLD";

export interface Payment {
  id: string;
  debtorAccount: string;
  creditorAccount: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  riskScore: number | null;
  riskReason: string | null;
  createdAt: string;
}

export interface NewPayment {
  debtorAccount: string;
  creditorAccount: string;
  amount: number;
  currency: string;
}

export interface ToolCall {
  tool: string;
  input: string;
  outcome: string;
  millis: number;
}

export interface AgentReply {
  reply: string;
  brain: string;
  conversationId: string;
  toolCalls: ToolCall[];
  usage: { inputTokens: number | null; outputTokens: number | null } | null;
}

export interface Approval {
  id: string;
  paymentId: string;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
  decidedAt: string | null;
  outcome: string | null;
}

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function listPayments(): Promise<Payment[]> {
  return json(await fetch("/api/payments"));
}

export async function createPayment(payment: NewPayment): Promise<Payment> {
  return json(
    await fetch("/api/payments", {
      method: "POST",
      // A fresh key per submission: if the network retries this POST, the server creates one payment, not two.
      headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify(payment),
    }),
  );
}

export async function askAgent(message: string, conversationId: string): Promise<AgentReply> {
  return json(
    await fetch("/agent/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, conversationId }),
    }),
  );
}

export async function listApprovals(): Promise<Approval[]> {
  return json(await fetch("/agent/approvals"));
}

export async function decideApproval(id: string, decision: "approve" | "reject"): Promise<Approval> {
  return json(await fetch(`/agent/approvals/${id}/${decision}`, { method: "POST" }));
}
