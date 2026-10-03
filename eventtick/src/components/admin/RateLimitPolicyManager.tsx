import { useState } from 'react';
import { Plus, Pencil, Trash2, Check, X, Loader2 } from 'lucide-react';
import {
  type DynamicPolicyResponse,
  createRateLimitPolicy,
  updateRateLimitPolicy,
  deleteRateLimitPolicy,
  ApiError,
} from '../../services/api';

const CATEGORIES = ['AUTH', 'CATALOG', 'BOOKING', 'USER', 'PAYMENT', 'ADMIN'];
const TIERS = ['PUBLIC', 'FREE', 'PRO', 'PREMIUM', 'ADMIN'];

interface Props {
  policies: DynamicPolicyResponse[];
  onPoliciesChange: (policies: DynamicPolicyResponse[]) => void;
}

interface EditState {
  replenishRate: string;
  burstCapacity: string;
  requestedTokens: string;
  enabled: boolean;
}

interface CreateState {
  category: string;
  tier: string;
  replenishRate: string;
  burstCapacity: string;
  requestedTokens: string;
}

export default function RateLimitPolicyManager({ policies, onPoliciesChange }: Props) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editState, setEditState] = useState<EditState | null>(null);
  const [creating, setCreating] = useState(false);
  const [createState, setCreateState] = useState<CreateState>({
    category: 'CATALOG', tier: 'FREE', replenishRate: '10', burstCapacity: '20', requestedTokens: '1',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  function clearMessages() {
    setError(null);
    setSuccess(null);
  }

  function startEdit(p: DynamicPolicyResponse) {
    clearMessages();
    setEditingId(p.id);
    setEditState({
      replenishRate: String(p.replenishRate),
      burstCapacity: String(p.burstCapacity),
      requestedTokens: String(p.requestedTokens),
      enabled: p.enabled,
    });
    setCreating(false);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditState(null);
  }

  async function saveEdit(id: string) {
    if (!editState) return;
    const rate = parseInt(editState.replenishRate, 10);
    const burst = parseInt(editState.burstCapacity, 10);
    const tokens = parseInt(editState.requestedTokens, 10);
    if (rate < 1 || burst < 1 || tokens < 0 || isNaN(rate) || isNaN(burst) || isNaN(tokens)) {
      setError('Rate and burst must be >= 1, tokens >= 0.');
      return;
    }
    clearMessages();
    setLoading(true);
    try {
      const updated = await updateRateLimitPolicy(id, {
        replenishRate: rate, burstCapacity: burst,
        requestedTokens: tokens || 1, enabled: editState.enabled,
      });
      onPoliciesChange(policies.map(p => p.id === id ? updated : p));
      setSuccess(`Updated ${updated.category}:${updated.tier}`);
      cancelEdit();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Update failed.');
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(p: DynamicPolicyResponse) {
    clearMessages();
    setLoading(true);
    try {
      await deleteRateLimitPolicy(p.id);
      onPoliciesChange(policies.filter(x => x.id !== p.id));
      setSuccess(`Deleted ${p.category}:${p.tier}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Delete failed.');
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate() {
    const rate = parseInt(createState.replenishRate, 10);
    const burst = parseInt(createState.burstCapacity, 10);
    const tokens = parseInt(createState.requestedTokens, 10);
    if (rate < 1 || burst < 1 || tokens < 0 || isNaN(rate) || isNaN(burst) || isNaN(tokens)) {
      setError('Rate and burst must be >= 1, tokens >= 0.');
      return;
    }
    clearMessages();
    setLoading(true);
    try {
      const created = await createRateLimitPolicy({
        category: createState.category, tier: createState.tier,
        replenishRate: rate, burstCapacity: burst,
        requestedTokens: tokens || 1,
      });
      onPoliciesChange([...policies, created]);
      setSuccess(`Created ${created.category}:${created.tier}`);
      setCreating(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Create failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-3">
      {error && <div className="text-error text-sm bg-error/10 px-3 py-2 rounded">{error}</div>}
      {success && <div className="text-success text-sm bg-success/10 px-3 py-2 rounded">{success}</div>}

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              {['Category', 'Tier', 'Rate/s', 'Burst', 'Cost/req', 'Enabled', 'Actions'].map(h => (
                <th key={h} className="text-left text-text-muted text-xs font-medium py-3 px-3 whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {policies.map(p => (
              <tr key={p.id} className="border-b border-border/50 hover:bg-bg-tertiary transition-colors">
                <td className="py-2 px-3 font-mono text-xs text-text-primary">{p.category}</td>
                <td className="py-2 px-3 font-mono text-xs text-text-secondary">{p.tier}</td>
                {editingId === p.id && editState ? (
                  <>
                    <td className="py-2 px-3"><input type="number" min="1" className="w-16 bg-bg-secondary border border-border rounded px-2 py-1 text-xs text-text-primary" value={editState.replenishRate} onChange={e => setEditState({ ...editState, replenishRate: e.target.value })} /></td>
                    <td className="py-2 px-3"><input type="number" min="1" className="w-16 bg-bg-secondary border border-border rounded px-2 py-1 text-xs text-text-primary" value={editState.burstCapacity} onChange={e => setEditState({ ...editState, burstCapacity: e.target.value })} /></td>
                    <td className="py-2 px-3"><input type="number" min="0" className="w-16 bg-bg-secondary border border-border rounded px-2 py-1 text-xs text-text-primary" value={editState.requestedTokens} onChange={e => setEditState({ ...editState, requestedTokens: e.target.value })} /></td>
                    <td className="py-2 px-3"><button onClick={() => setEditState({ ...editState, enabled: !editState.enabled })} className={`text-xs px-2 py-1 rounded ${editState.enabled ? 'bg-success/20 text-success' : 'bg-error/20 text-error'}`}>{editState.enabled ? 'Yes' : 'No'}</button></td>
                    <td className="py-2 px-3 flex gap-1">
                      <button onClick={() => saveEdit(p.id)} disabled={loading} className="p-1 rounded hover:bg-success/20 text-success" title="Save"><Check size={14} /></button>
                      <button onClick={cancelEdit} className="p-1 rounded hover:bg-bg-tertiary text-text-muted" title="Cancel"><X size={14} /></button>
                    </td>
                  </>
                ) : (
                  <>
                    <td className="py-2 px-3 text-text-secondary">{p.replenishRate}</td>
                    <td className="py-2 px-3 text-text-secondary">{p.burstCapacity}</td>
                    <td className="py-2 px-3 text-text-secondary">{p.requestedTokens}</td>
                    <td className="py-2 px-3">
                      <span className={`text-xs px-2 py-0.5 rounded ${p.enabled ? 'bg-success/20 text-success' : 'bg-error/20 text-error'}`}>{p.enabled ? 'Active' : 'Disabled'}</span>
                    </td>
                    <td className="py-2 px-3 flex gap-1">
                      <button onClick={() => startEdit(p)} className="p-1 rounded hover:bg-bg-tertiary text-text-muted" title="Edit"><Pencil size={14} /></button>
                      <button onClick={() => handleDelete(p)} disabled={loading} className="p-1 rounded hover:bg-error/20 text-error" title="Delete"><Trash2 size={14} /></button>
                    </td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {loading && <div className="flex items-center gap-2 text-text-muted text-xs"><Loader2 size={14} className="animate-spin" /> Saving...</div>}

      {creating ? (
        <div className="flex flex-wrap items-end gap-2 bg-bg-secondary p-3 rounded border border-border">
          <label className="flex flex-col gap-1 text-xs text-text-muted">
            Category
            <select value={createState.category} onChange={e => setCreateState({ ...createState, category: e.target.value })} className="bg-bg-primary border border-border rounded px-2 py-1 text-text-primary text-xs">
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-xs text-text-muted">
            Tier
            <select value={createState.tier} onChange={e => setCreateState({ ...createState, tier: e.target.value })} className="bg-bg-primary border border-border rounded px-2 py-1 text-text-primary text-xs">
              {TIERS.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-xs text-text-muted">
            Rate/s
            <input type="number" min="1" value={createState.replenishRate} onChange={e => setCreateState({ ...createState, replenishRate: e.target.value })} className="w-16 bg-bg-primary border border-border rounded px-2 py-1 text-text-primary text-xs" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-text-muted">
            Burst
            <input type="number" min="1" value={createState.burstCapacity} onChange={e => setCreateState({ ...createState, burstCapacity: e.target.value })} className="w-16 bg-bg-primary border border-border rounded px-2 py-1 text-text-primary text-xs" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-text-muted">
            Cost/req
            <input type="number" min="0" value={createState.requestedTokens} onChange={e => setCreateState({ ...createState, requestedTokens: e.target.value })} className="w-16 bg-bg-primary border border-border rounded px-2 py-1 text-text-primary text-xs" />
          </label>
          <div className="flex gap-1">
            <button onClick={handleCreate} disabled={loading} className="btn-primary text-xs px-3 py-1.5">Create</button>
            <button onClick={() => { setCreating(false); clearMessages(); }} className="btn-secondary text-xs px-3 py-1.5">Cancel</button>
          </div>
        </div>
      ) : (
        <button onClick={() => { setCreating(true); cancelEdit(); clearMessages(); }} className="flex items-center gap-1.5 text-xs text-accent hover:text-accent-lighter transition-colors">
          <Plus size={14} /> Add policy
        </button>
      )}
    </div>
  );
}
