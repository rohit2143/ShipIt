import React from 'react';
import { Copy, ShieldCheck } from 'lucide-react';

export const SettingsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="rounded-xl bg-slate-900 border border-slate-800 p-5 shadow-sm space-y-4">
        <div className="flex items-center gap-2 pb-2 border-b border-slate-800">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <h2 className="text-sm font-bold text-slate-100">GitHub Webhook & Auto-Deploy Configuration</h2>
        </div>

        <div className="space-y-3 text-xs">
          <div>
            <label className="block text-slate-400 mb-1">Webhook Ingress URL</label>
            <div className="flex items-center gap-2">
              <input 
                type="text" 
                readOnly 
                value="https://shipit.local/api/v1/github/webhook" 
                className="w-full font-mono text-xs p-2 rounded border border-slate-800 bg-slate-950 text-slate-300 select-all" 
              />
              <button 
                onClick={() => alert('Webhook URL copied to clipboard!')}
                className="px-3 py-2 rounded bg-blue-600 hover:bg-blue-500 text-white font-medium flex items-center gap-1 transition-colors"
              >
                <Copy className="w-3.5 h-3.5" />
                <span>Copy</span>
              </button>
            </div>
          </div>

          <div>
            <label className="block text-slate-400 mb-1">HMAC-SHA256 Webhook Secret</label>
            <input 
              type="password" 
              readOnly 
              value="whsec_09183491823091820391283" 
              className="w-full font-mono text-xs p-2 rounded border border-slate-800 bg-slate-950 text-slate-400" 
            />
          </div>

          <div className="pt-2 flex items-center gap-2">
            <input 
              type="checkbox" 
              id="autoDeploy" 
              defaultChecked 
              className="accent-blue-600 rounded" 
            />
            <label htmlFor="autoDeploy" className="text-xs font-medium text-slate-300 cursor-pointer">
              Enable Automatic Push-to-Deploy on branch <code className="font-mono bg-slate-950 px-1 py-0.5 rounded border border-slate-800 text-blue-400">main</code>
            </label>
          </div>
        </div>
      </div>
    </div>
  );
};
