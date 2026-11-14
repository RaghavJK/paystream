import { FormEvent, useEffect, useRef, useState } from "react";
import {
  AgentReply,
  Approval,
  Payment,
  askAgent,
  createPayment,
  decideApproval,
  listApprovals,
  listPayments,
} from "./api";

// TODO (roadmap step 9): replace 2-second polling with Server-Sent Events or a WebSocket.
const POLL_MS = 2000;

export default function App() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const load = () =>
      listPayments()
        .then((p) => {
          if (active) {
            setPayments(p);
            setError(null);
          }
        })
        .catch(() => active && setError("Cannot reach payment-service on port 8081. Is it running?"));
    load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  return (
    <main>
      <header>
        <h1>PayStream</h1>
        <p>Create a payment, watch fraud scoring arrive over Kafka, then ask the agent about it.</p>
      </header>
      {error && <p className="error">{error}</p>}
      <div className="columns">
        <section>
          <h2>New payment</h2>
          <PaymentForm onCreated={(p) => setPayments((all) => [p, ...all])} />
          <h2>Recent payments</h2>
          <PaymentTable payments={payments} />
        </section>
        <section>
          <h2>Dispute-triage agent</h2>
          <AgentPanel />
        </section>
      </div>
    </main>
  );
}

function PaymentForm({ onCreated }: { onCreated: (p: Payment) => void }) {
  const [debtorAccount, setDebtor] = useState("ACC-1001");
  const [creditorAccount, setCreditor] = useState("ACC-2002");
  const [amount, setAmount] = useState("2500");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      onCreated(await createPayment({ debtorAccount, creditorAccount, amount: Number(amount), currency: "INR" }));
    } catch {
      setMessage("Payment was not created. Check the amount and that payment-service is running.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <label>
        Debtor account
        <input value={debtorAccount} onChange={(e) => setDebtor(e.target.value)} required />
      </label>
      <label>
        Creditor account
        <input value={creditorAccount} onChange={(e) => setCreditor(e.target.value)} required />
      </label>
      <label>
        Amount (INR)
        <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
      </label>
      <button disabled={busy}>{busy ? "Sending..." : "Send payment"}</button>
      <p className="hint">Try 250000, or a creditor starting with WATCH, to trigger a fraud flag.</p>
      {message && <p className="error">{message}</p>}
    </form>
  );
}

function PaymentTable({ payments }: { payments: Payment[] }) {
  if (payments.length === 0) {
    return <p className="hint">No payments yet.</p>;
  }
  return (
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>To</th>
          <th>Amount</th>
          <th>Status</th>
          <th>Risk</th>
        </tr>
      </thead>
      <tbody>
        {payments.map((p) => (
          <tr key={p.id}>
            <td>
              <button className="link" title="Copy payment ID" onClick={() => navigator.clipboard?.writeText(p.id)}>
                {p.id.slice(0, 8)}
              </button>
            </td>
            <td>{p.creditorAccount}</td>
            <td>
              {p.amount.toLocaleString()} {p.currency}
            </td>
            <td>
              <span className={`badge ${p.status.toLowerCase()}`}>{p.status.replace("_", " ")}</span>
            </td>
            <td>{p.riskScore === null ? "scoring..." : `${p.riskScore}: ${p.riskReason}`}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function AgentPanel() {
  // One conversation per page load, so the agent remembers which payment you were discussing.
  const conversationId = useRef(crypto.randomUUID());
  const [question, setQuestion] = useState("");
  const [reply, setReply] = useState<AgentReply | null>(null);
  const [approvals, setApprovals] = useState<Approval[]>([]);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  const refreshApprovals = () =>
    listApprovals()
      .then(setApprovals)
      .catch(() => undefined);

  useEffect(() => {
    refreshApprovals();
  }, []);

  async function ask(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setFailed(false);
    try {
      setReply(await askAgent(question, conversationId.current));
      await refreshApprovals();
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }

  async function decide(id: string, decision: "approve" | "reject") {
    try {
      await decideApproval(id, decision);
    } finally {
      await refreshApprovals();
    }
  }

  return (
    <>
      <form onSubmit={ask}>
        <label>
          Question
          <textarea
            rows={3}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Click a payment ID to copy it, then ask: Why is payment <id> flagged? Follow up with: please hold it."
            required
          />
        </label>
        <button disabled={busy}>{busy ? "Thinking..." : "Ask agent"}</button>
        {failed && <p className="error">Cannot reach agent-service on port 8083.</p>}
        {reply && (
          <div className="reply">
            <p>{reply.reply}</p>
            <p className="hint">Answered by: {reply.brain}</p>
            {reply.toolCalls.length > 0 && (
              <ul className="tools">
                {reply.toolCalls.map((call, index) => (
                  <li key={index}>
                    {call.tool} · {call.outcome} · {call.millis} ms
                  </li>
                ))}
              </ul>
            )}
            {reply.usage && (
              <p className="hint">
                Tokens: {reply.usage.inputTokens ?? "?"} in, {reply.usage.outputTokens ?? "?"} out
              </p>
            )}
          </div>
        )}
      </form>

      <h2>Hold requests awaiting a human</h2>
      {approvals.length === 0 && <p className="hint">None. The agent can only propose a hold; you decide.</p>}
      {approvals.map((approval) => (
        <div className="reply approval" key={approval.id}>
          <p>
            Payment {approval.paymentId.slice(0, 8)}: {approval.reason}
          </p>
          {approval.status === "PENDING" ? (
            <div className="actions">
              <button type="button" onClick={() => decide(approval.id, "approve")}>
                Approve
              </button>
              <button type="button" className="secondary" onClick={() => decide(approval.id, "reject")}>
                Reject
              </button>
            </div>
          ) : (
            <p className="hint">
              {approval.status}. {approval.outcome}
            </p>
          )}
        </div>
      ))}
    </>
  );
}
