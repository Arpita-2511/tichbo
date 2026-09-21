import { BrowserRouter, Routes, Route, Outlet, Link, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import { AppProvider } from './context/AppContext';
import Navbar from './components/layout/Navbar';
import Footer from './components/layout/Footer';
import SearchModal from './components/common/SearchModal';
import LocationSelector from './components/common/LocationSelector';
import EmptyState from './components/common/EmptyState';

import Home from './pages/Home';
import Movies from './pages/Movies';
import Sports from './pages/Sports';
import Concerts from './pages/Concerts';
import Theatre from './pages/Theatre';
import Events from './pages/Events';
import EventDetails from './pages/EventDetails';
import SeatSelection from './pages/SeatSelection';
import BookingSummary from './pages/BookingSummary';
import Ticket from './pages/Ticket';
import Bookings from './pages/Bookings';
import Login from './pages/Login';
import Signup from './pages/Signup';
import Profile from './pages/Profile';
import Plans from './pages/Plans';
import Admin from './pages/Admin';

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  return null;
}

function Layout() {
  return (
    <div className="min-h-screen flex flex-col bg-bg-primary">
      <ScrollToTop />
      <Navbar />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      <SearchModal />
      <LocationSelector />
    </div>
  );
}

function NotFound() {
  return (
    <EmptyState
      title="Page not found"
      description="The page you're looking for doesn't exist."
      action={<Link to="/" className="btn-primary">Go Home</Link>}
    />
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<Home />} />
            <Route path="/movies" element={<Movies />} />
            <Route path="/sports" element={<Sports />} />
            <Route path="/concerts" element={<Concerts />} />
            <Route path="/theatre" element={<Theatre />} />
            <Route path="/events" element={<Events />} />
            <Route path="/event/:id" element={<EventDetails />} />
            <Route path="/booking/:showId/seats" element={<SeatSelection />} />
            <Route path="/booking/new/summary" element={<BookingSummary />} />
            <Route path="/ticket/:id" element={<Ticket />} />
            <Route path="/bookings" element={<Bookings />} />
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/plans" element={<Plans />} />
            <Route path="/admin" element={<Admin />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </AppProvider>
    </BrowserRouter>
  );
}
