import { useState } from "react";
import { useAuth } from "../contexts/AuthContext";

export default function Login() {
    const { login } = useAuth();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [isLoading, setIsLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setIsLoading(true);

        try {
            await login(username, password);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Login failed");
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div style={{
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'linear-gradient(135deg, #020617 0%, #0f172a 50%, #082f49 100%)',
        }}>
            <div style={{
                width: '100%',
                maxWidth: '400px',
                padding: '32px',
                background: 'rgba(30, 41, 59, 0.8)',
                backdropFilter: 'blur(16px)',
                borderRadius: '16px',
                border: '1px solid rgba(255, 255, 255, 0.1)',
                boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
            }}>
                <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                    <div style={{
                        width: '64px',
                        height: '64px',
                        background: 'linear-gradient(135deg, #0ea5e9 0%, #0369a1 100%)',
                        borderRadius: '12px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        margin: '0 auto 16px',
                        boxShadow: '0 10px 40px rgba(14, 165, 233, 0.3)',
                    }}>
                        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2">
                            <path d="M3 21h18M5 21V7l8-4 8 4v14M9 21v-4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v4" />
                        </svg>
                    </div>
                    <h1 style={{ color: 'white', fontSize: '24px', fontWeight: 'bold', margin: '0' }}>
                        Super Admin Panel
                    </h1>
                    <p style={{ color: '#94a3b8', marginTop: '8px' }}>
                        Manage your companies and services
                    </p>
                </div>

                <form onSubmit={handleSubmit}>
                    {error && (
                        <div style={{
                            padding: '12px',
                            marginBottom: '16px',
                            borderRadius: '8px',
                            background: 'rgba(239, 68, 68, 0.1)',
                            border: '1px solid rgba(239, 68, 68, 0.3)',
                            color: '#f87171',
                            fontSize: '14px',
                        }}>
                            {error}
                        </div>
                    )}

                    <div style={{ marginBottom: '16px' }}>
                        <label style={{ display: 'block', color: '#cbd5e1', fontSize: '14px', marginBottom: '8px' }}>
                            Username
                        </label>
                        <input
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '8px',
                                background: 'rgba(15, 23, 42, 0.5)',
                                border: '1px solid #475569',
                                color: 'white',
                                fontSize: '16px',
                                outline: 'none',
                                boxSizing: 'border-box',
                            }}
                            placeholder="Enter your username"
                            required
                        />
                    </div>

                    <div style={{ marginBottom: '24px' }}>
                        <label style={{ display: 'block', color: '#cbd5e1', fontSize: '14px', marginBottom: '8px' }}>
                            Password
                        </label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '8px',
                                background: 'rgba(15, 23, 42, 0.5)',
                                border: '1px solid #475569',
                                color: 'white',
                                fontSize: '16px',
                                outline: 'none',
                                boxSizing: 'border-box',
                            }}
                            placeholder="Enter your password"
                            required
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={isLoading}
                        style={{
                            width: '100%',
                            padding: '12px 16px',
                            borderRadius: '8px',
                            background: isLoading ? '#475569' : 'linear-gradient(90deg, #0284c7 0%, #0ea5e9 100%)',
                            color: 'white',
                            fontSize: '16px',
                            fontWeight: '500',
                            border: 'none',
                            cursor: isLoading ? 'not-allowed' : 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px',
                        }}
                    >
                        {isLoading ? (
                            <>
                                <span style={{
                                    width: '20px',
                                    height: '20px',
                                    border: '2px solid transparent',
                                    borderTopColor: 'white',
                                    borderRadius: '50%',
                                    animation: 'spin 1s linear infinite',
                                }}></span>
                                Signing in...
                            </>
                        ) : (
                            "Sign In"
                        )}
                    </button>
                </form>

                <p style={{ textAlign: 'center', color: '#64748b', fontSize: '12px', marginTop: '24px' }}>
                    tvGO Super Admin Panel v1.0
                </p>
            </div>

            <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
        input::placeholder {
          color: #64748b;
        }
        input:focus {
          border-color: #0ea5e9;
          box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.2);
        }
        button:hover:not(:disabled) {
          filter: brightness(1.1);
        }
      `}</style>
        </div>
    );
}
