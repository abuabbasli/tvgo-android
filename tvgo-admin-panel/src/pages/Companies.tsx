import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import api, { type Company, type CompanyServices } from "../lib/api";

export default function Companies() {
    const { user, signOut } = useAuth();
    const queryClient = useQueryClient();
    const [search, setSearch] = useState("");
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [editingCompany, setEditingCompany] = useState<Company | null>(null);
    const [showPasswordModal, setShowPasswordModal] = useState<Company | null>(null);
    const [newPassword, setNewPassword] = useState("");

    const { data, isLoading } = useQuery({
        queryKey: ["companies", search],
        queryFn: () => api.superAdmin.listCompanies(0, 100, search || undefined),
    });

    const deleteMutation = useMutation({
        mutationFn: api.superAdmin.deleteCompany,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ["companies"] }),
    });

    const resetPasswordMutation = useMutation({
        mutationFn: ({ id, password }: { id: string; password: string }) =>
            api.superAdmin.resetPassword(id, password),
        onSuccess: () => {
            setShowPasswordModal(null);
            setNewPassword("");
            queryClient.invalidateQueries({ queryKey: ["companies"] });
        },
    });

    const inputStyle = {
        width: '100%',
        padding: '12px 16px',
        borderRadius: '8px',
        background: 'rgba(15, 23, 42, 0.5)',
        border: '1px solid #475569',
        color: 'white',
        fontSize: '16px',
        outline: 'none',
        boxSizing: 'border-box' as const,
    };

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
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <Link to="/" style={{ color: '#94a3b8', textDecoration: 'none' }}>← Back</Link>
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
                                <path d="M3 21h18M5 21V7l8-4 8 4v14" />
                            </svg>
                        </div>
                        <div>
                            <h1 style={{ color: 'white', fontSize: '20px', fontWeight: 'bold', margin: 0 }}>Companies</h1>
                            <p style={{ color: '#94a3b8', fontSize: '14px', margin: 0 }}>Manage tenant companies</p>
                        </div>
                    </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <span style={{ color: '#cbd5e1' }}>{user?.displayName || user?.username}</span>
                    <button onClick={signOut} style={{
                        padding: '8px 16px', borderRadius: '8px', background: '#1e293b',
                        color: '#cbd5e1', border: '1px solid #475569', cursor: 'pointer',
                    }}>Sign Out</button>
                </div>
            </header>

            {/* Main Content */}
            <main style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px' }}>
                {/* Actions Bar */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px' }}>
                    <input
                        type="text"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Search companies..."
                        style={{ ...inputStyle, width: '320px', paddingLeft: '40px' }}
                    />
                    <button
                        onClick={() => setShowCreateModal(true)}
                        style={{
                            padding: '12px 24px', borderRadius: '8px',
                            background: 'linear-gradient(90deg, #0284c7 0%, #0ea5e9 100%)',
                            color: 'white', border: 'none', cursor: 'pointer', fontWeight: '500',
                        }}
                    >
                        + Add Company
                    </button>
                </div>

                {/* Table */}
                <div style={{
                    background: 'rgba(30, 41, 59, 0.7)',
                    backdropFilter: 'blur(16px)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '12px',
                    overflow: 'hidden',
                }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                        <thead>
                            <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.1)' }}>
                                <th style={{ textAlign: 'left', padding: '16px 24px', color: '#94a3b8', fontWeight: '500' }}>Company</th>
                                <th style={{ textAlign: 'left', padding: '16px 24px', color: '#94a3b8', fontWeight: '500' }}>Username</th>
                                <th style={{ textAlign: 'left', padding: '16px 24px', color: '#94a3b8', fontWeight: '500' }}>Services</th>
                                <th style={{ textAlign: 'center', padding: '16px 24px', color: '#94a3b8', fontWeight: '500' }}>Status</th>
                                <th style={{ textAlign: 'right', padding: '16px 24px', color: '#94a3b8', fontWeight: '500' }}>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {isLoading ? (
                                <tr><td colSpan={5} style={{ textAlign: 'center', padding: '32px', color: '#94a3b8' }}>Loading...</td></tr>
                            ) : data?.items.length === 0 ? (
                                <tr><td colSpan={5} style={{ textAlign: 'center', padding: '32px', color: '#94a3b8' }}>No companies found. Create one to get started.</td></tr>
                            ) : (
                                data?.items.map((company) => (
                                    <tr key={company.id} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                                        <td style={{ padding: '16px 24px' }}>
                                            <p style={{ color: 'white', fontWeight: '500', margin: 0 }}>{company.name}</p>
                                            <p style={{ color: '#64748b', fontSize: '14px', margin: '4px 0 0' }}>{company.slug}</p>
                                        </td>
                                        <td style={{ padding: '16px 24px', color: '#cbd5e1' }}>{company.username}</td>
                                        <td style={{ padding: '16px 24px' }}>
                                            <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                                                {company.services.enable_channels && <span style={{ padding: '4px 8px', borderRadius: '4px', background: 'rgba(59, 130, 246, 0.2)', color: '#60a5fa', fontSize: '12px' }}>CH</span>}
                                                {company.services.enable_vod && <span style={{ padding: '4px 8px', borderRadius: '4px', background: 'rgba(168, 85, 247, 0.2)', color: '#c084fc', fontSize: '12px' }}>VOD</span>}
                                                {company.services.enable_games && <span style={{ padding: '4px 8px', borderRadius: '4px', background: 'rgba(249, 115, 22, 0.2)', color: '#fb923c', fontSize: '12px' }}>GM</span>}
                                                {company.services.enable_messaging && <span style={{ padding: '4px 8px', borderRadius: '4px', background: 'rgba(34, 197, 94, 0.2)', color: '#4ade80', fontSize: '12px' }}>MSG</span>}
                                            </div>
                                        </td>
                                        <td style={{ padding: '16px 24px', textAlign: 'center' }}>
                                            {company.is_active ? (
                                                <span style={{ padding: '4px 12px', borderRadius: '12px', background: 'rgba(34, 197, 94, 0.2)', color: '#4ade80', fontSize: '12px' }}>Active</span>
                                            ) : (
                                                <span style={{ padding: '4px 12px', borderRadius: '12px', background: 'rgba(239, 68, 68, 0.2)', color: '#f87171', fontSize: '12px' }}>Inactive</span>
                                            )}
                                        </td>
                                        <td style={{ padding: '16px 24px', textAlign: 'right' }}>
                                            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                                                <button onClick={() => setEditingCompany(company)} style={{ padding: '8px', borderRadius: '6px', background: 'transparent', border: '1px solid #475569', color: '#94a3b8', cursor: 'pointer' }}>Edit</button>
                                                <button onClick={() => setShowPasswordModal(company)} style={{ padding: '8px', borderRadius: '6px', background: 'transparent', border: '1px solid #475569', color: '#eab308', cursor: 'pointer' }}>Key</button>
                                                <button onClick={() => { if (confirm(`Delete ${company.name}?`)) deleteMutation.mutate(company.id); }} style={{ padding: '8px', borderRadius: '6px', background: 'transparent', border: '1px solid #475569', color: '#f87171', cursor: 'pointer' }}>Del</button>
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </main>

            {/* Create/Edit Modal */}
            {(showCreateModal || editingCompany) && (
                <CompanyModal company={editingCompany} onClose={() => { setShowCreateModal(false); setEditingCompany(null); }} />
            )}

            {/* Password Reset Modal */}
            {showPasswordModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50 }}>
                    <div style={{ background: 'rgba(30, 41, 59, 0.95)', backdropFilter: 'blur(16px)', borderRadius: '12px', padding: '24px', width: '100%', maxWidth: '400px', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                        <h2 style={{ color: 'white', fontSize: '20px', fontWeight: 'bold', marginBottom: '16px' }}>Reset Password</h2>
                        <p style={{ color: '#94a3b8', marginBottom: '16px' }}>Reset password for <span style={{ color: 'white' }}>{showPasswordModal.name}</span></p>
                        <input
                            type="text"
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            placeholder="New password"
                            style={{ ...inputStyle, marginBottom: '16px' }}
                        />
                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                            <button onClick={() => setShowPasswordModal(null)} style={{ padding: '10px 20px', borderRadius: '8px', background: '#334155', color: 'white', border: 'none', cursor: 'pointer' }}>Cancel</button>
                            <button onClick={() => resetPasswordMutation.mutate({ id: showPasswordModal.id, password: newPassword })} disabled={!newPassword} style={{ padding: '10px 20px', borderRadius: '8px', background: '#0284c7', color: 'white', border: 'none', cursor: 'pointer', opacity: newPassword ? 1 : 0.5 }}>Reset</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

function CompanyModal({ company, onClose }: { company: Company | null; onClose: () => void }) {
    const queryClient = useQueryClient();
    const isEdit = !!company;

    const [formData, setFormData] = useState({
        name: company?.name || "",
        slug: company?.slug || "",
        username: company?.username || "",
        password: "",
        is_active: company?.is_active ?? true,
        services: company?.services || {
            enable_vod: true,
            enable_channels: true,
            enable_games: false,
            enable_messaging: false,
        },
    });

    const createMutation = useMutation({
        mutationFn: api.superAdmin.createCompany,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["companies"] });
            queryClient.invalidateQueries({ queryKey: ["super-admin-stats"] });
            onClose();
        },
    });

    const updateMutation = useMutation({
        mutationFn: ({ id, data }: { id: string; data: { name?: string; is_active?: boolean; services?: CompanyServices } }) =>
            api.superAdmin.updateCompany(id, data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["companies"] });
            onClose();
        },
    });

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (isEdit) {
            updateMutation.mutate({ id: company.id, data: { name: formData.name, is_active: formData.is_active, services: formData.services } });
        } else {
            createMutation.mutate({ name: formData.name, slug: formData.slug, username: formData.username, password: formData.password, services: formData.services });
        }
    };

    const handleNameChange = (name: string) => {
        setFormData({ ...formData, name, slug: name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") });
    };

    const inputStyle = {
        width: '100%',
        padding: '12px 16px',
        borderRadius: '8px',
        background: 'rgba(15, 23, 42, 0.5)',
        border: '1px solid #475569',
        color: 'white',
        fontSize: '16px',
        outline: 'none',
        boxSizing: 'border-box' as const,
        marginBottom: '16px',
    };

    const services = [
        { key: 'enable_channels', label: 'Channels', color: '#3b82f6' },
        { key: 'enable_vod', label: 'VOD / Movies', color: '#a855f7' },
        { key: 'enable_games', label: 'Games', color: '#f97316' },
        { key: 'enable_messaging', label: 'Messaging', color: '#22c55e' },
    ];

    return (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50 }}>
            <div style={{ background: 'rgba(30, 41, 59, 0.95)', backdropFilter: 'blur(16px)', borderRadius: '12px', padding: '24px', width: '100%', maxWidth: '500px', maxHeight: '90vh', overflowY: 'auto', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <h2 style={{ color: 'white', fontSize: '20px', fontWeight: 'bold', marginBottom: '24px' }}>
                    {isEdit ? "Edit Company" : "Create Company"}
                </h2>

                <form onSubmit={handleSubmit}>
                    <label style={{ display: 'block', color: '#cbd5e1', fontSize: '14px', marginBottom: '8px' }}>Company Name</label>
                    <input type="text" value={formData.name} onChange={(e) => handleNameChange(e.target.value)} style={inputStyle} placeholder="Acme Corp" required />

                    {!isEdit && (
                        <>
                            <label style={{ display: 'block', color: '#cbd5e1', fontSize: '14px', marginBottom: '8px' }}>Slug</label>
                            <input type="text" value={formData.slug} onChange={(e) => setFormData({ ...formData, slug: e.target.value })} style={inputStyle} placeholder="acme-corp" required />

                            <label style={{ display: 'block', color: '#cbd5e1', fontSize: '14px', marginBottom: '8px' }}>Username</label>
                            <input type="text" value={formData.username} onChange={(e) => setFormData({ ...formData, username: e.target.value })} style={inputStyle} placeholder="acme" required />

                            <label style={{ display: 'block', color: '#cbd5e1', fontSize: '14px', marginBottom: '8px' }}>Password</label>
                            <input type="text" value={formData.password} onChange={(e) => setFormData({ ...formData, password: e.target.value })} style={inputStyle} placeholder="Enter password" required />
                        </>
                    )}

                    {isEdit && (
                        <label style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px', cursor: 'pointer' }}>
                            <input type="checkbox" checked={formData.is_active} onChange={(e) => setFormData({ ...formData, is_active: e.target.checked })} style={{ width: '20px', height: '20px' }} />
                            <span style={{ color: 'white' }}>Active</span>
                        </label>
                    )}

                    <div style={{ borderTop: '1px solid #475569', paddingTop: '16px', marginTop: '8px' }}>
                        <h3 style={{ color: 'white', fontWeight: '500', marginBottom: '12px' }}>Services</h3>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                            {services.map((service) => (
                                <label
                                    key={service.key}
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '12px',
                                        padding: '12px',
                                        borderRadius: '8px',
                                        cursor: 'pointer',
                                        background: formData.services[service.key as keyof CompanyServices] ? `${service.color}20` : 'rgba(15, 23, 42, 0.5)',
                                        border: `1px solid ${formData.services[service.key as keyof CompanyServices] ? service.color : '#475569'}`,
                                    }}
                                >
                                    <input
                                        type="checkbox"
                                        checked={formData.services[service.key as keyof CompanyServices]}
                                        onChange={(e) => setFormData({ ...formData, services: { ...formData.services, [service.key]: e.target.checked } })}
                                        style={{ width: '18px', height: '18px', accentColor: service.color }}
                                    />
                                    <span style={{ color: 'white' }}>{service.label}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '24px' }}>
                        <button type="button" onClick={onClose} style={{ padding: '10px 20px', borderRadius: '8px', background: '#334155', color: 'white', border: 'none', cursor: 'pointer' }}>Cancel</button>
                        <button type="submit" disabled={createMutation.isPending || updateMutation.isPending} style={{ padding: '10px 20px', borderRadius: '8px', background: '#0284c7', color: 'white', border: 'none', cursor: 'pointer' }}>
                            {isEdit ? "Save Changes" : "Create Company"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
