import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Rocket } from 'lucide-react';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#080B14] flex flex-col items-center justify-center space-y-4 font-sans select-none relative overflow-hidden">
        <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-purple-700 via-purple-600 to-indigo-500 text-white flex items-center justify-center shadow-[0_0_24px_-3px_rgba(139,92,246,0.6)] border border-purple-400/30 animate-pulse">
          <Rocket className="w-6 h-6 text-purple-100" />
        </div>
        <div className="flex flex-col items-center space-y-1">
          <span className="text-sm font-bold tracking-tight text-white font-mono">ShipIt PaaS</span>
          <span className="text-xs text-slate-400 font-mono">Authenticating session...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
};
