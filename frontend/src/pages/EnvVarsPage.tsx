import React, { useState } from 'react';
import { KeyRound, Eye, EyeOff, Trash2, Plus } from 'lucide-react';

export const EnvVarsPage: React.FC = () => {
  const [showDbPass, setShowDbPass] = useState(false);

  return (
    <div className="space-y-6">
      <div className="rounded-xl bg-slate-900 border border-slate-800 p-5 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-3 border-b border-slate-800">
          <div>
            <div className="flex items-center gap-2">
              <KeyRound className="w-4 h-4 text-indigo-400" />
              <h2 className="text-sm font-bold text-slate-100">Cryptographic Environment Variables</h2>
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-mono">
                AES-256-GCM + AAD
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Secrets are encrypted at rest with 256-bit keys and bound to this project's ID.
            </p>
          </div>

          <button className="px-3 py-1.5 text-xs font-medium rounded-md bg-blue-600 hover:bg-blue-500 text-white shadow-sm flex items-center gap-1.5 transition-colors">
            <Plus className="w-3.5 h-3.5" />
            <span>Add Variable</span>
          </button>
        </div>

        <div className="space-y-2">
          {/* Var 1 */}
          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between text-xs">
            <div className="flex items-center gap-3">
              <span className="font-mono font-bold text-slate-200">SPRING_PROFILES_ACTIVE</span>
              <span className="font-mono text-slate-400">production</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-900 border border-slate-800 text-slate-400">
                Plaintext
              </span>
              <button className="text-rose-400 hover:text-rose-300 p-1">
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Var 2 */}
          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between text-xs">
            <div className="flex items-center gap-3">
              <span className="font-mono font-bold text-slate-200">DB_PASSWORD</span>
              <span className="font-mono text-slate-400 tracking-wider">
                {showDbPass ? 'superSecretPostgres2026!' : '••••••••••••••••'}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-mono">
                v1:AES-256-GCM
              </span>
              <button 
                onClick={() => setShowDbPass(!showDbPass)}
                className="text-indigo-400 hover:text-indigo-300 p-1 flex items-center gap-1 text-[11px]"
              >
                {showDbPass ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                <span>{showDbPass ? 'Hide' : 'Reveal'}</span>
              </button>
              <button className="text-rose-400 hover:text-rose-300 p-1">
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
