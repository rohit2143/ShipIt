import React from 'react';
import { Activity, Server, Cpu, HardDrive } from 'lucide-react';

export const ContainersPage: React.FC = () => {
  return (
    <div className="space-y-6">
      {/* Container Card */}
      <div className="rounded-xl bg-slate-900 border border-slate-800 p-5 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-3 border-b border-slate-800">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
              <h2 className="text-sm font-bold text-slate-100">shipit-c-payment-service-9412</h2>
              <span className="px-2 py-0.5 rounded text-[10px] bg-slate-950 font-mono border border-slate-800 text-slate-400">
                ID: d48fe39109a2
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">Running multi-stage Spring Boot container under UID 10001</p>
          </div>

          <div className="flex items-center gap-2">
            <button className="px-3 py-1.5 rounded-md border border-slate-700 hover:bg-slate-800 text-xs font-medium text-slate-300 transition-colors">
              Restart Container
            </button>
            <button className="px-3 py-1.5 rounded-md border border-rose-500/30 text-rose-400 hover:bg-rose-500/10 text-xs font-medium transition-colors">
              Stop
            </button>
          </div>
        </div>

        {/* Telemetry Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800">
            <span className="text-slate-400 text-[11px] flex items-center gap-1.5">
              <Activity className="w-3.5 h-3.5 text-emerald-400" />
              Health Probe
            </span>
            <div className="text-emerald-400 font-bold text-sm mt-1">UP (HTTP 200)</div>
            <span className="text-[10px] text-slate-400 font-mono">latency: 4ms</span>
          </div>

          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800">
            <span className="text-slate-400 text-[11px] flex items-center gap-1.5">
              <Server className="w-3.5 h-3.5 text-blue-400" />
              Host Port
            </span>
            <div className="text-slate-100 font-mono font-bold text-sm mt-1">10042 : 8080</div>
            <span className="text-[10px] text-slate-400">Collision-free</span>
          </div>

          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800">
            <span className="text-slate-400 text-[11px] flex items-center gap-1.5">
              <HardDrive className="w-3.5 h-3.5 text-indigo-400" />
              Memory Limit
            </span>
            <div className="text-slate-100 font-mono font-bold text-sm mt-1">246 MB / 512 MB</div>
            <div className="w-full bg-slate-900 h-1 rounded-full mt-1.5 overflow-hidden border border-slate-800">
              <div className="bg-emerald-500 h-full rounded-full" style={{ width: '48%' }}></div>
            </div>
          </div>

          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800">
            <span className="text-slate-400 text-[11px] flex items-center gap-1.5">
              <Cpu className="w-3.5 h-3.5 text-amber-400" />
              CPU & PIDs
            </span>
            <div className="text-slate-100 font-mono font-bold text-sm mt-1">0.8% • 18 PIDs</div>
            <span className="text-[10px] text-slate-400">Uptime: 4h 12m</span>
          </div>
        </div>
      </div>
    </div>
  );
};
