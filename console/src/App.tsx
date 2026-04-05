import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useState } from 'react';
import { getToken, clearToken } from './api';
import Login from './pages/Login';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Tenants from './pages/Tenants';
import TenantConfig from './pages/TenantConfig';
import Users from './pages/Users';

export default function App() {
  const [authed, setAuthed] = useState(!!getToken());

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={
          authed ? <Navigate to="/" /> : <Login onLogin={() => setAuthed(true)} />
        } />
        <Route element={
          authed ? <Layout onLogout={() => { clearToken(); setAuthed(false); }} /> : <Navigate to="/login" />
        }>
          <Route path="/" element={<Dashboard />} />
          <Route path="/tenants" element={<Tenants />} />
          <Route path="/tenants/:id" element={<TenantConfig />} />
          <Route path="/users" element={<Users />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
