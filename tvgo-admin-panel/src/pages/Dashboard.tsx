import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../contexts/AuthContext";
import api from "../lib/api";
import { Link } from "react-router-dom";

export default function Dashboard() {
    const { user, signOut } = useAuth();

    const { data: stats, isLoading } = useQuery({
        queryKey: ["super-admin-stats"],
        queryFn: api.superAdmin.getStats,
    });

    const statCards = [
        { label: "Total Companies", value: stats?.total_companies || 0, color: "#3b82f6" },
        { label: "Active Companies", value: stats?.active_companies || 0, color: "#22c55e" },
        { label: "Total Users", value: stats?.total_users || 0, color: "#a855f7" },
        { label: "Total Channels", value: stats?.total_channels || 0, color: "#f97316" },
        { label: "Total Movies", value: stats?.total_movies || 0, color: "#ec4899" },
    ];

    return (
        <div style={{ minHeight: '100vh', background: '#0f172a' }}>
            {/* Header */}
            <header style={{
                background: 'rgba(30, 41, 59, 0.8)',
                backdropFilter: 'blur(16px)',
                borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
                padding: '16px 32px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <div style={{
                        width: '40px',
                        height: '40px',
                        background: 'linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%)',
                        borderRadius: '8px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}>
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2">
                            <path d="M3 21h18M5 21V7l8-4 8 4v14M9 21v-4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v4" />
                        </svg>
                    </div>
                    <div>
                        <h1 style={{ color: 'white', fontSize: '20px', fontWeight: 'bold', margin: 0 }}>Super Admin</h1>
                        <p style={{ color: '#94a3b8', fontSize: '14px', margin: 0 }}>tvGO Management</p>
                    </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <span style={{ color: '#cbd5e1' }}>Welcome, {user?.displayName || user?.username}</span>
                    <button
                        onClick={signOut}
                        style={{
                            padding: '8px 16px',
                            borderRadius: '8px',
                            background: '#1e293b',
                            color: '#cbd5e1',
                            border: '1px solid #475569',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                        }}
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
                        </svg>
                        Sign Out
                    </button>
                </div>
            </header>

            {/* Main Content */}
            <main style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px' }}>
                {/* Stats Grid */}
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: '24px',
                    marginBottom: '32px',
                }}>
                    {statCards.map((stat, index) => (
                        <div
                            key={index}
                            style={{
                                background: 'rgba(30, 41, 59, 0.7)',
                                backdropFilter: 'blur(16px)',
                                border: '1px solid rgba(255, 255, 255, 0.1)',
                                borderRadius: '12px',
                                padding: '24px',
                            }}
                        >
                            <div style={{
                                width: '48px',
                                height: '48px',
                                borderRadius: '8px',
                                background: stat.color,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                marginBottom: '16px',
                            }}>
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2">
                                    <path d="M3 21h18M5 21V7l8-4 8 4v14M9 21v-4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v4" />
                                </svg>
                            </div>
                            <p style={{ color: '#94a3b8', fontSize: '14px', margin: 0 }}>{stat.label}</p>
                            <p style={{ color: 'white', fontSize: '32px', fontWeight: 'bold', margin: '4px 0 0' }}>
                                {isLoading ? "..." : stat.value.toLocaleString()}
                            </p>
                        </div>
                    ))}
                </div>

                {/* Quick Actions */}
                <div style={{
                    background: 'rgba(30, 41, 59, 0.7)',
                    backdropFilter: 'blur(16px)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '12px',
                    padding: '24px',
                }}>
                    <h2 style={{ color: 'white', fontSize: '18px', fontWeight: '600', marginBottom: '16px' }}>
                        Quick Actions
                    </h2>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px' }}>
                        <Link to="/companies" style={{ textDecoration: 'none' }}>
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '16px',
                                padding: '16px',
                                borderRadius: '8px',
                                background: 'rgba(15, 23, 42, 0.5)',
                                border: '1px solid #475569',
                                cursor: 'pointer',
                            }}>
                                <div style={{
                                    width: '48px',
                                    height: '48px',
                                    borderRadius: '8px',
                                    background: 'rgba(14, 165, 233, 0.2)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                }}>
                                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#38bdf8" strokeWidth="2">
                                        <path d="M3 21h18M5 21V7l8-4 8 4v14M9 21v-4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v4" />
                                    </svg>
                                </div>
                                <div>
                                    <p style={{ color: 'white', fontWeight: '500', margin: 0 }}>Manage Companies</p>
                                    <p style={{ color: '#94a3b8', fontSize: '14px', margin: '4px 0 0' }}>Create and manage tenant companies</p>
                                </div>
                            </div>
                        </Link>

                        <Link to="/companies" style={{ textDecoration: 'none' }}>
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '16px',
                                padding: '16px',
                                borderRadius: '8px',
                                background: 'rgba(15, 23, 42, 0.5)',
                                border: '1px solid #475569',
                                cursor: 'pointer',
                            }}>
                                <div style={{
                                    width: '48px',
                                    height: '48px',
                                    borderRadius: '8px',
                                    background: 'rgba(34, 197, 94, 0.2)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                }}>
                                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#4ade80" strokeWidth="2">
                                        <path d="M12 5v14M5 12h14" />
                                    </svg>
                                </div>
                                <div>
                                    <p style={{ color: 'white', fontWeight: '500', margin: 0 }}>Add New Company</p>
                                    <p style={{ color: '#94a3b8', fontSize: '14px', margin: '4px 0 0' }}>Create a new tenant company</p>
                                </div>
                            </div>
                        </Link>
                    </div>
                </div>
            </main>
        </div>
    );
}
