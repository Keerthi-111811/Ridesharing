import React, { useState, useEffect, useCallback } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import axios from 'axios';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import {
  Car, User, LogOut, Mail, Lock, Eye, EyeOff, Check, X,
  MapPin, Users, Calendar, Wallet, Plus, Search,
  Ticket, Menu, X as XIcon, Star, Phone, Send,
  RefreshCw, AlertCircle, Shield, Clock, MapPinned
} from 'lucide-react';
import './App.css';

const API_URL = 'http://localhost:8080';
function Auth({ setToken, setUser }) {
  // Add missing isLogin state
  const [isLogin, setIsLogin] = useState(true);
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [showForgotOtpVerification, setShowForgotOtpVerification] = useState(false);
  const [showOtpVerification, setShowOtpVerification] = useState(false);
  const [showRegisterOtp, setShowRegisterOtp] = useState(false);
  const [registerWith, setRegisterWith] = useState('email');

  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [otp, setOtp] = useState('');
  const [registerOtp, setRegisterOtp] = useState('');
  const [registerEmail, setRegisterEmail] = useState('');
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');

  const handleLogin = async (e) => {
    e.preventDefault();
    setMessage('');
    try {
      const response = await axios.post(`${API_URL}/auth/login`, {
        emailOrPhone: loginIdentifier,
        password: loginPassword
      });
      if (response.data.token) {
        const userData = {
          name: response.data.name || '',
          email: response.data.email || '',
          phone: response.data.phone || '',
          vehicleModel: response.data.vehicleModel || '',
          licensePlate: response.data.licensePlate || '',
          vehicleCapacity: response.data.vehicleCapacity || '',
          userType: response.data.userType || 'passenger'
        };
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(userData));
        setToken(response.data.token);
        setUser(userData);
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Login failed');
      setMessageType('error');
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setMessage('');
    try {
      const response = await axios.post(`${API_URL}/auth/register`, {
        name: form.name,
        email: registerWith === 'email' ? form.email : '',
        phone: registerWith === 'phone' ? form.phone : '',
        password: form.password
      });
      if (response.data.requiresVerification) {
        setRegisterEmail(response.data.email);
        setShowRegisterOtp(true);
        setMessage('OTP sent to your email.');
        setMessageType('success');
      } else if (response.data.token) {
        const userData = {
          name: response.data.name,
          email: response.data.email,
          phone: response.data.phone,
          userType: response.data.userType || 'passenger'
        };
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(userData));
        setToken(response.data.token);
        setUser(userData);
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Registration failed');
      setMessageType('error');
    }
  };

  const handleSendOtp = async () => {
    setMessage('');
    if (!form.phone || form.phone.length !== 10) {
      setMessage('Please enter a valid 10-digit phone number');
      setMessageType('error');
      return;
    }
    try {
      const response = await axios.post(`${API_URL}/auth/send-otp`, { phone: form.phone });
      if (response.data.message === 'OTP sent successfully') {
        setShowOtpVerification(true);
        setMessage('OTP sent to your phone');
        setMessageType('success');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Failed to send OTP');
      setMessageType('error');
    }
  };

  const handleVerifyRegisterOtp = async () => {
    setMessage('');
    try {
      const response = await axios.post(`${API_URL}/auth/verify-register-otp`, {
        email: registerEmail,
        otp: registerOtp
      });
      if (response.data.token) {
        const userData = {
          name: response.data.name,
          email: response.data.email,
          phone: response.data.phone,
          userType: response.data.userType || 'passenger'
        };
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(userData));
        setToken(response.data.token);
        setUser(userData);
        setShowRegisterOtp(false);
        setRegisterOtp('');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Verification failed');
      setMessageType('error');
    }
  };

  const handleResendRegisterOtp = async () => {
    try {
      await axios.post(`${API_URL}/auth/resend-register-otp`, { email: registerEmail });
      setMessage('OTP resent successfully!');
      setMessageType('success');
    } catch (error) {
      setMessage(error.response?.data?.message || 'Failed to resend OTP');
      setMessageType('error');
    }
  };

  const handleVerifyOtp = async () => {
    setMessage('');
    try {
      const response = await axios.post(`${API_URL}/auth/verify-otp`, {
        phone: form.phone,
        otp: otp
      });
      if (response.data.token) {
        const userData = {
          name: response.data.name,
          email: response.data.email,
          phone: response.data.phone,
          userType: response.data.userType || 'passenger'
        };
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(userData));
        setToken(response.data.token);
        setUser(userData);
        setShowOtpVerification(false);
        setOtp('');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'OTP verification failed');
      setMessageType('error');
    }
  };

  const handleForgotPassword = async (e) => {
    e.preventDefault();
    setMessage('');
    try {
      const response = await axios.post(`${API_URL}/auth/forgot-password`, {
        email: loginIdentifier
      });
      if (response.data.message) {
        setForgotEmail(loginIdentifier);
        setShowForgotPassword(false);
        setShowForgotOtpVerification(true);
        setMessage(response.data.message);
        setMessageType('success');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Failed to send reset link');
      setMessageType('error');
    }
  };

  const handleForgotVerifyOtp = async () => {
    setMessage('');
    if (!newPassword || !confirmNewPassword) {
      setMessage('Please enter and confirm your new password');
      setMessageType('error');
      return;
    }
    if (newPassword !== confirmNewPassword) {
      setMessage('Passwords do not match');
      setMessageType('error');
      return;
    }
    try {
      const response = await axios.post(`${API_URL}/auth/reset-password`, {
        email: forgotEmail,
        otp: otp,
        newPassword: newPassword
      });
      if (response.data.message) {
        setMessage('Password reset successful! Redirecting...');
        setMessageType('success');
        setTimeout(() => {
          setShowForgotOtpVerification(false);
          setOtp('');
          setNewPassword('');
          setConfirmNewPassword('');
          setForgotEmail('');
        }, 2000);
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Reset failed');
      setMessageType('error');
    }
  };

  const switchRegisterMethod = (method) => {
    setRegisterWith(method);
    setForm({ ...form, email: '', phone: '' });
    setMessage('');
  };

  return (
    <div className="auth-page">
      {/* LEFT SIDE - Image */}
      <div className="auth-left">
        <img src={require('./rideshare-illustration.png')} alt="Ride Sharing" />
      </div>

      {/* RIGHT SIDE - Auth Content */}
      <div className="auth-right">
        <div className="auth-container">
          {/* Logo and Brand Section - FIXED */}
          <div className="auth-brand">
            <div className="auth-logo">
              <img src={require('./logo.png')} alt="RideSync" style={{ height: '150px', width: 'auto' }} />
              {/* REMOVED the auth-logo-text span */}
            </div>
          </div>
          {/* Rest of your code remains exactly the same */}
          {/* Auth Card with all existing features */}
          <div className="auth-card">
            <h2>
              {showRegisterOtp ? 'Verify Email OTP' :
               showForgotOtpVerification ? 'Reset Password' :
               showForgotPassword ? 'Forgot Password' :
               showOtpVerification ? 'Verify Phone OTP' :
               (isLogin ? 'Welcome Back' : 'Create Account')}
            </h2>

            {/* Registration OTP Verification */}
            {showRegisterOtp && (
              <div className="auth-form">
                <div className="otp-info">
                  <p>Enter the OTP sent to:</p>
                  <p className="otp-email">{registerEmail}</p>
                </div>
                <div className="form-group">
                  <label><Check size={18} /> Enter OTP</label>
                  <input
                    type="text"
                    placeholder="Enter 6-digit OTP"
                    value={registerOtp}
                    onChange={(e) => setRegisterOtp(e.target.value)}
                    maxLength={6}
                  />
                </div>
                <button onClick={handleVerifyRegisterOtp} className="btn-primary">
                  <Check size={18} /> Verify & Complete Registration
                </button>
                <div className="otp-actions">
                  <button onClick={handleResendRegisterOtp} className="btn-secondary">
                    Resend OTP
                  </button>
                  <button onClick={() => { setShowRegisterOtp(false); setRegisterOtp(''); }} className="btn-secondary">
                    Back
                  </button>
                </div>
              </div>
            )}

            {/* Forgot Password Form */}
            {showForgotPassword && !showForgotOtpVerification && (
              <form onSubmit={handleForgotPassword} className="auth-form">
                <div className="form-group">
                  <label><Mail size={18} /> Email Address</label>
                  <input
                    type="email"
                    placeholder="Enter your registered email"
                    value={loginIdentifier}
                    onChange={(e) => setLoginIdentifier(e.target.value)}
                  />
                </div>
                <button type="submit" className="btn-primary">
                  <Send size={18} /> Send Reset Link
                </button>
                <p className="switch-mode" onClick={() => { setShowForgotPassword(false); setMessage(''); }}>
                  Back to Login
                </p>
              </form>
            )}

            {/* Forgot OTP Verification */}
            {showForgotOtpVerification && (
              <div className="auth-form">
                <div className="otp-info">
                  <p>Enter OTP sent to:</p>
                  <p className="otp-email">{forgotEmail}</p>
                </div>
                <div className="form-group">
                  <label><Check size={18} /> Enter OTP</label>
                  <input
                    type="text"
                    placeholder="Enter 6-digit OTP"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                    maxLength={6}
                  />
                </div>
                <div className="form-group">
                  <label><Lock size={18} /> New Password</label>
                  <input
                    type="password"
                    placeholder="Enter new password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label><Lock size={18} /> Confirm Password</label>
                  <input
                    type="password"
                    placeholder="Confirm new password"
                    value={confirmNewPassword}
                    onChange={(e) => setConfirmNewPassword(e.target.value)}
                  />
                </div>
                <button onClick={handleForgotVerifyOtp} className="btn-primary">
                  <Check size={18} /> Reset Password
                </button>
                <p className="switch-mode" onClick={() => { setShowForgotOtpVerification(false); setShowForgotPassword(true); setOtp(''); }}>
                  Back
                </p>
              </div>
            )}

            {/* Phone OTP Verification */}
            {showOtpVerification && (
              <div className="auth-form">
                <div className="form-group">
                  <label><Phone size={18} /> Enter OTP</label>
                  <input
                    type="text"
                    placeholder="Enter 6-digit OTP"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                    maxLength={6}
                  />
                </div>
                <button onClick={handleVerifyOtp} className="btn-primary">
                  <Check size={18} /> Verify OTP
                </button>
                <p className="switch-mode" onClick={() => { setShowOtpVerification(false); setOtp(''); }}>
                  Back to Registration
                </p>
              </div>
            )}

            {/* Login Form */}
            {!showForgotPassword && !showForgotOtpVerification && !showOtpVerification && !showRegisterOtp && isLogin && (
              <form onSubmit={handleLogin} className="auth-form">
                <div className="form-group">
                  <label> Email or Phone</label>
                  <input
                    type="text"
                    placeholder="Enter email or phone number"
                    value={loginIdentifier}
                    onChange={(e) => setLoginIdentifier(e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label> Password</label>
                  <div className="password-input">
                    <input
                      type={showPassword ? "text" : "password"}
                      placeholder="Enter your password"
                      value={loginPassword}
                      onChange={(e) => setLoginPassword(e.target.value)}
                    />
                    <button type="button" onClick={() => setShowPassword(!showPassword)}>
                      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>
                <button type="submit" className="btn-primary">Login</button>
                <p className="forgot-password-link" onClick={() => { setShowForgotPassword(true); setMessage(''); }}>
                  Forgot Password?
                </p>
              </form>
            )}

            {/* Registration Form */}
            {!showForgotPassword && !showForgotOtpVerification && !showOtpVerification && !showRegisterOtp && !isLogin && (
              <form onSubmit={handleRegister} className="auth-form">
                <div className="register-toggle">
                  <button type="button" className={`toggle-btn ${registerWith === 'email' ? 'active' : ''}`} onClick={() => switchRegisterMethod('email')}>
                    <Mail size={16} /> Email
                  </button>
                  <button type="button" className={`toggle-btn ${registerWith === 'phone' ? 'active' : ''}`} onClick={() => switchRegisterMethod('phone')}>
                    <Phone size={16} /> Phone
                  </button>
                </div>

                <div className="form-group">
                  <label><User size={18} /> Full Name</label>
                  <input type="text" placeholder="Enter your full name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                </div>

                {registerWith === 'email' ? (
                  <div className="form-group">
                    <label><Mail size={18} /> Email Address</label>
                    <input type="email" placeholder="Enter your email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
                  </div>
                ) : (
                  <div className="form-group">
                    <label><Phone size={18} /> Phone Number</label>
                    <input type="tel" placeholder="Enter your phone number" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} maxLength={10} />
                    {form.phone === '6305373547' && <span className="success-hint">✅ Test number - OTP: 123456</span>}
                  </div>
                )}

                <div className="form-group">
                  <label><Lock size={18} /> Password</label>
                  <input type="password" placeholder="Create a password (min 6 characters)" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
                </div>

                {registerWith === 'phone' && !showOtpVerification && (
                  <button type="button" onClick={handleSendOtp} className="btn-secondary">
                    <Send size={16} /> Send OTP
                  </button>
                )}

                <button type="submit" className="btn-primary">
                  <Check size={18} /> Register
                </button>
              </form>
            )}

            {message && (
              <div className={`message ${messageType}`}>
                {messageType === 'success' ? <Check size={18} /> : <AlertCircle size={18} />}
                {message}
              </div>
            )}

            {!showForgotPassword && !showForgotOtpVerification && !showOtpVerification && !showRegisterOtp && (
              <p className="switch-mode" onClick={() => { setIsLogin(!isLogin); setMessage(''); }}>
                {isLogin ? "Don't have an account? Register" : "Already have an account? Login"}
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ==================== DASHBOARD COMPONENT ====================
function Dashboard({ token, user, setToken, setUser }) {
  const [activeTab, setActiveTab] = useState('home');
  const [userType, setUserType] = useState(user?.userType || 'passenger');
  const [rides, setRides] = useState([]);
  const [myRides, setMyRides] = useState([]);
  const [myBookings, setMyBookings] = useState([]);
  const [userHistory, setUserHistory] = useState({ postedRides: [], bookedRides: [] });
  const [searchForm, setSearchForm] = useState({ source: '', destination: '',date: '',seats: ''});
  const [form, setForm] = useState({ source: '', destination: '', availableSeats: '', dateTime: '', pricePerSeat: '' });
  const [vehicleForm, setVehicleForm] = useState({ vehicleModel: '', licensePlate: '', vehicleCapacity: '' });
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [selectedSeats, setSelectedSeats] = useState({});
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(null);
  const [showCancelConfirm, setShowCancelConfirm] = useState(null);

  const navigate = useNavigate();

  const fetchUserData = useCallback(async () => {
    try {
      const response = await axios.get(`${API_URL}/user/profile`,
        { headers: { Authorization: `Bearer ${token}` } }
      );

      if (response.data.user) {
        const userData = response.data.user;

        // Update user state
        setUser(userData);

        // Update localStorage
        localStorage.setItem('user', JSON.stringify(userData));

        // Update vehicleForm
        setVehicleForm({
          vehicleModel: userData.vehicleModel || '',
          licensePlate: userData.vehicleLicensePlate || userData.licensePlate || '',
          vehicleCapacity: userData.vehicleCapacity || ''
        });

        // Update userType if needed
        setUserType(userData.userType || 'passenger');
      }
    } catch (error) {
      console.error('Error fetching user data:', error);
    }
  }, [token, setUser]);
  const fetchRides = useCallback(async () => {
    try {
      const params = {};
      if (searchForm.source) params.source = searchForm.source;
      if (searchForm.destination) params.destination = searchForm.destination;
      if (searchForm.date) params.date = searchForm.date;
      if (searchForm.seats) params.seats = searchForm.seats;

      const response = await axios.get(`${API_URL}/rides`, {
        params: params,
        headers: { Authorization: `Bearer ${token}` }
      });
      console.log("Rides fetched:", response.data);
      setRides(response.data);
    } catch (error) {
      console.error('Error fetching rides:', error);
      setRides([]); // Ensure rides is empty on error
    }
  }, [token, searchForm.source, searchForm.destination, searchForm.date, searchForm.seats]);


  const fetchMyRides = useCallback(async () => {
    try {
      const response = await axios.get(`${API_URL}/rides/my-rides`, { headers: { Authorization: `Bearer ${token}` } });
      setMyRides(response.data);
    } catch (error) { console.error('Error fetching my rides:', error); }
  }, [token]);

  const fetchMyBookings = useCallback(async () => {
    try {
      const endpoint = userType === 'driver' ? '/bookings/ride-requests' : '/bookings/my-bookings';
      const response = await axios.get(`${API_URL}${endpoint}`, { headers: { Authorization: `Bearer ${token}` } });
      setMyBookings(response.data);
    } catch (error) { console.error('Error fetching bookings:', error); }
  }, [token, userType]);

  const fetchUserHistory = useCallback(async () => {
    try {
      const response = await axios.get(`${API_URL}/user/history`, { headers: { Authorization: `Bearer ${token}` } });
      setUserHistory(response.data);
    } catch (error) { console.error('Error fetching history:', error); }
  }, [token]);

  // Separate effect for passenger
 useEffect(() => {
   if (token && userType === 'passenger') {
     fetchMyBookings();
   }
 }, [token, userType]);

  // Separate effect for driver
  useEffect(() => {
    if (token && userType === 'driver') {
      fetchMyRides();
      fetchMyBookings();
    }
  }, [token, userType]);

  // User history effect
  useEffect(() => {
    if (token) {
      fetchUserHistory();
    }
  }, [token]);

  useEffect(() => {
    if (user) {
      setVehicleForm({
        vehicleModel: user.vehicleModel || '',
        licensePlate: user.licensePlate || '',
        vehicleCapacity: user.vehicleCapacity || ''
      });
    }
  }, [user]);

  const handleSearch = (e) => {
    e.preventDefault();
    fetchRides();
  };

  const deleteRide = async (rideId) => {
    setMessage('');
    try {
      await axios.delete(`${API_URL}/rides/${rideId}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setMessage('Ride deleted successfully!');
      setMessageType('success');
      if (userType === 'driver') {
        fetchMyRides();
      }
      setShowDeleteConfirm(null);
      setTimeout(() => setMessage(''), 3000);
    } catch (error) {
      setMessage(error.response?.data?.message || 'Failed to delete ride');
      setMessageType('error');
    }
  };
  const cancelBooking = async (bookingId) => {
    setMessage('');
    try {
      const response = await axios.put(`${API_URL}/bookings/${bookingId}/cancel`,
        {}, // Empty body since we don't need to send anything
        { headers: { Authorization: `Bearer ${token}` } }
      );

      if (response.data.message) {
        setMessage('Booking cancelled successfully!');
        setMessageType('success');
        fetchMyBookings(); // Refresh the bookings list
        setShowCancelConfirm(null); // Close the modal
      }
    } catch (error) {
      console.error('Error cancelling booking:', error);
      setMessage(error.response?.data?.message || 'Failed to cancel booking');
      setMessageType('error');
    }
  };

  const postRide = async () => {
    setMessage('');
    if (!form.source || !form.destination || !form.availableSeats || !selectedDate) {
      setMessage('Please fill in all required fields');
      setMessageType('error');
      return;
    }

    try {
      const formattedDate = selectedDate.toISOString().slice(0, 19).replace('T', ' ');

      const rideData = {
        source: form.source,
        destination: form.destination,
        availableSeats: parseInt(form.availableSeats),
        dateTime: formattedDate,
        pricePerSeat: form.pricePerSeat ? parseFloat(form.pricePerSeat) : 0,
        vehicleModel: vehicleForm.vehicleModel,
        licensePlate: vehicleForm.licensePlate,
        vehicleCapacity: vehicleForm.vehicleCapacity ? parseInt(vehicleForm.vehicleCapacity) : parseInt(form.availableSeats)
      };

      const response = await axios.post(`${API_URL}/rides`, rideData, {
        headers: { Authorization: `Bearer ${token}` }
      });

      if (response.data.ride) {
        // Refresh user data to get updated vehicle info
        await fetchUserData();

        setMessage('Ride posted successfully!');
        setMessageType('success');
        setForm({ source: '', destination: '', availableSeats: '', dateTime: '', pricePerSeat: '' });
        setSelectedDate(new Date());
        fetchMyRides();
        setActiveTab('myrides');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Failed to post ride');
      setMessageType('error');
    }
  };

  const bookRide = async (rideId) => {
    setMessage('');
    const seatsToBook = selectedSeats[rideId] || 1;
    if (seatsToBook < 1) {
      setMessage('Please select at least 1 seat');
      setMessageType('error');
      return;
    }
    try {
      const response = await axios.post(`${API_URL}/bookings`,
        { rideId, seatsBooked: seatsToBook },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      if (response.data.booking) {
        setMessage(`${seatsToBook} seat${seatsToBook > 1 ? 's' : ''} booked successfully!`);
        setMessageType('success');
        fetchMyBookings();
        setSelectedSeats(prev => {
          const newState = { ...prev };
          delete newState[rideId];
          return newState;
        });
        fetchRides();
        setActiveTab('bookings'); // Switch to bookings tab after booking
      }
    } catch (error) {
      setMessage(error.response?.data?.message || 'Failed to book ride');
      setMessageType('error');
    }
  };

  const updateBookingStatus = async (bookingId, status) => {
    try {
      await axios.put(`${API_URL}/bookings/${bookingId}`, { status }, { headers: { Authorization: `Bearer ${token}` } });
      fetchMyBookings();
    } catch (error) {
      console.error('Error updating booking:', error);
      setMessage(error.response?.data?.message || 'Failed to update booking');
      setMessageType('error');
    }
  };

  const saveVehicleDetails = async () => {
    setMessage('');

    if (!vehicleForm.vehicleModel || !vehicleForm.licensePlate || !vehicleForm.vehicleCapacity) {
      setMessage('Please fill all vehicle details');
      setMessageType('error');
      return;
    }

    try {
      const response = await axios.put(`${API_URL}/user/vehicle`, vehicleForm, {
        headers: { Authorization: `Bearer ${token}` }
      });

      if (response.data.message) {
        // IMPORTANT: Fetch updated user data from backend
        const userResponse = await axios.get(`${API_URL}/user/profile`, {
          headers: { Authorization: `Bearer ${token}` }
        });

        if (userResponse.data.user) {
          // Update both state and localStorage with complete user data
          const updatedUser = {
            ...user,
            ...userResponse.data.user, // This will include vehicle details
            vehicleModel: vehicleForm.vehicleModel,
            licensePlate: vehicleForm.licensePlate,
            vehicleCapacity: vehicleForm.vehicleCapacity
          };

          setUser(updatedUser);
          localStorage.setItem('user', JSON.stringify(updatedUser));

          // Also update vehicleForm to match
          setVehicleForm({
            vehicleModel: updatedUser.vehicleModel || '',
            licensePlate: updatedUser.licensePlate || '',
            vehicleCapacity: updatedUser.vehicleCapacity || ''
          });
        }

        setMessage('Vehicle details saved successfully!');
        setMessageType('success');
        setTimeout(() => setMessage(''), 3000);
      }
    } catch (error) {
      console.error('Error saving vehicle:', error);
      setMessage(error.response?.data?.message || 'Failed to save vehicle details');
      setMessageType('error');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
    navigate('/');
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleString('en-IN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    });
  };

  const switchUserType = async (type) => {
    if (type === userType) return;

    setMessage('');

    // Optimistic update - update local state immediately
    setUserType(type);

    // Update localStorage
    const updatedUser = { ...user, userType: type };
    setUser(updatedUser);
    localStorage.setItem('user', JSON.stringify(updatedUser));

    // Set the active tab based on user type
    if (type === 'passenger') {
      setActiveTab('home'); // Passengers go to Find Rides
    } else {
      setActiveTab('post'); // Drivers go directly to Post Ride
    }

    setMessage(`Switched to ${type} mode`);
    setMessageType('success');

    setTimeout(() => setMessage(''), 2000);

    try {
      await axios.post(`${API_URL}/user/update-user-type`,
        { userType: type },
        { headers: { Authorization: `Bearer ${token}` } }
      );
    } catch (e) {
      console.log('Backend sync skipped');
    }
  };

  return (
    <div className="dashboard">
      <header className="header">
        <div className="header-left">
          <button className="mobile-menu-btn" onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}>
            {isMobileMenuOpen ? <XIcon size={24} /> : <Menu size={24} />}
          </button>
          <Car className="logo-icon" size={28} />
          <h1>RideSync</h1>
        </div>
        <div className={`header-right ${isMobileMenuOpen ? 'active' : ''}`}>
          <div className="user-badge"><User size={18} /><span>{user?.name || 'User'}</span></div>
          <button onClick={handleLogout} className="btn-logout"><LogOut size={18} /> Logout</button>
        </div>
      </header>

      <div className="user-type-switcher">
        <button className={`user-type-btn ${userType === 'passenger' ? 'active' : ''}`} onClick={() => switchUserType('passenger')}>
          <User size={18} /> Passenger
        </button>
        <button className={`user-type-btn ${userType === 'driver' ? 'active' : ''}`} onClick={() => switchUserType('driver')}>
          <Car size={18} /> Driver
        </button>
      </div>

      <div className="tabs">
        {userType === 'passenger' && (
          <>
            <button className={`tab ${activeTab === 'home' ? 'active' : ''}`} onClick={() => setActiveTab('home')}>
              <Search size={18} /> Find Rides
            </button>
            <button className={`tab ${activeTab === 'bookings' ? 'active' : ''}`} onClick={() => setActiveTab('bookings')}>
              <Ticket size={18} /> My Bookings
            </button>
          </>
        )}

        {userType === 'driver' && (
          <>
            <button className={`tab ${activeTab === 'post' ? 'active' : ''}`} onClick={() => setActiveTab('post')}>
              <Plus size={18} /> Post Ride
            </button>
            <button className={`tab ${activeTab === 'myrides' ? 'active' : ''}`} onClick={() => setActiveTab('myrides')}>
              <Car size={18} /> My Rides
            </button>
          </>
        )}

        <button className={`tab ${activeTab === 'profile' ? 'active' : ''}`} onClick={() => setActiveTab('profile')}>
          <User size={18} /> Profile
        </button>
      </div>

      <main className="main-content">
        {message && <div className={`toast ${messageType}`}>{messageType === 'success' ? <Check size={18} /> : <AlertCircle size={18} />}{message}</div>}
        {/* Cancel Booking Confirmation Modal */}
        {showCancelConfirm && (
          <div className="modal-overlay" onClick={() => setShowCancelConfirm(null)}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <div className="modal-header">
                <h3>Cancel Booking</h3>
                <button
                  className="modal-close-btn"
                  onClick={() => setShowCancelConfirm(null)}
                  aria-label="Close"
                >
                  <X size={20} />
                </button>
              </div>
              <p>Are you sure you want to cancel this booking? This action cannot be undone.</p>
              <div className="modal-actions">
                <button
                  onClick={() => cancelBooking(showCancelConfirm)}
                  className="btn-danger"
                >
                  <X size={16} /> Yes, Cancel Booking
                </button>
                <button
                  onClick={() => setShowCancelConfirm(null)}
                  className="btn-secondary"
                >
                  No, Keep it
                </button>
              </div>
            </div>
          </div>
        )}
        {/* Delete Ride Confirmation Modal */}
        {showDeleteConfirm && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h3>Delete Ride</h3>
              <p>Are you sure you want to delete this ride? This action cannot be undone.</p>
              <div className="modal-actions">
                <button onClick={() => deleteRide(showDeleteConfirm)} className="btn-danger">
                  <X size={16} /> Yes, Delete
                </button>
                <button onClick={() => setShowDeleteConfirm(null)} className="btn-secondary">
                  No, Keep it
                </button>
              </div>
            </div>
          </div>


        )}
        {/* Profile Tab */}
        {activeTab === 'profile' && (
          <div className="tab-panel">
            <div className="profile-section">
              <div className="profile-header">
                <div className="profile-avatar">
                  <User size={50} />
                </div>
                <h2>{user?.name || 'User'}</h2>
                <p>{user?.email || user?.phone || 'No email provided'}</p>
                <span className="user-type-badge">
                  {userType === 'driver' ? '🚗 Driver' : '👤 Passenger'}
                </span>
              </div>

              <div className="profile-card">
                <h3><User size={18} /> Personal Information</h3>
                <p className="info-note">Your personal details are secure</p>
                <div className="profile-info">
                  <div className="info-row">
                    <span className="label">Name</span>
                    <span className="value">{user?.name || 'Not provided'}</span>
                  </div>
                  <div className="info-row">
                    <span className="label">Email</span>
                    <span className="value">{user?.email || 'Not provided'}</span>
                  </div>
                  <div className="info-row">
                    <span className="label">Phone</span>
                    <span className="value">{user?.phone || 'Not provided'}</span>
                  </div>
                  <div className="info-row">
                    <span className="label">User Type</span>
                    <span className="value">{userType === 'driver' ? 'Driver' : 'Passenger'}</span>
                  </div>
                </div>
              </div>

              {userType === 'driver' && (
                <div className="profile-card">
                  <h3><Car size={18} /> Vehicle Information</h3>
                  <div className="profile-info">
                    <div className="info-row">
                      <span className="label">Vehicle Model</span>
                      <span className="value">{vehicleForm.vehicleModel || user?.vehicleModel || 'Not set'}</span>
                    </div>
                    <div className="info-row">
                      <span className="label">License Plate</span>
                      <span className="value">{vehicleForm.licensePlate || user?.licensePlate || 'Not set'}</span>
                    </div>
                    <div className="info-row">
                      <span className="label">Seating Capacity</span>
                      <span className="value">{vehicleForm.vehicleCapacity || user?.vehicleCapacity || 'Not set'}</span>
                    </div>
                    <div className="info-row">
                      <span className="label">Rating</span>
                      <span className="value">{user?.rating ? `${user.rating} ⭐` : 'New Driver'}</span>
                    </div>
                  </div>
                </div>
              )}

              <div className="history-section">
                <h4><RefreshCw size={18} /> Recent Activity</h4>
                {userType === 'driver' && userHistory.postedRides?.length > 0 && (
                  <div className="history-item">
                    <div className="history-route">
                      <Car size={14} /> {userHistory.postedRides.length} rides posted
                    </div>
                  </div>
                )}
                {userType === 'passenger' && userHistory.bookedRides?.length > 0 && (
                  <div className="history-item">
                    <div className="history-route">
                      <Ticket size={14} /> {userHistory.bookedRides.length} rides booked
                    </div>
                  </div>
                )}
                {((userType === 'driver' && !userHistory.postedRides?.length) ||
                  (userType === 'passenger' && !userHistory.bookedRides?.length)) && (
                  <p className="empty-text">No recent activity</p>
                )}
              </div>

              <button onClick={handleLogout} className="btn-logout-full">
                <LogOut size={18} /> Logout
              </button>
            </div>
          </div>
        )}

        {/* Driver Home - Dashboard */}
        {activeTab === 'home' && userType === 'driver' && (
          <div className="tab-panel">
            <div className="dashboard-welcome">
              <div className="welcome-header">
                <Car size={32} />
                <div>
                  <h2>Welcome, {user?.name}!</h2>
                  <p>Driver Dashboard</p>
                </div>
              </div>
              <div className="stats-grid">
                <div className="stat-card">
                  <Car size={24} />
                  <div className="stat-info">
                    <span className="stat-value">{myRides.length}</span>
                    <span className="stat-label">Total Rides</span>
                  </div>
                </div>
                <div className="stat-card">
                  <Users size={24} />
                  <div className="stat-info">
                    <span className="stat-value">{myBookings.length}</span>
                    <span className="stat-label">Bookings</span>
                  </div>
                </div>
                <div className="stat-card">
                  <Star size={24} />
                  <div className="stat-info">
                    <span className="stat-value">{user?.rating || 'New'}</span>
                    <span className="stat-label">Rating</span>
                  </div>
                </div>
              </div>
              <div className="quick-actions">
                <h3>Quick Actions</h3>
                <div className="action-buttons">
                  <button className="action-btn" onClick={() => setActiveTab('post')}>
                    <Plus size={32} />
                    <span>Post New Ride</span>
                  </button>
                  <button className="action-btn" onClick={() => setActiveTab('myrides')}>
                    <Car size={32} />
                    <span>My Rides</span>
                  </button>
                  <button className="action-btn" onClick={() => setActiveTab('profile')}>
                    <User size={32} />
                    <span>Profile</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Driver - Post Ride */}
        {activeTab === 'post' && userType === 'driver' && (
          <div className="tab-panel">
            <div className="form-section">
              <div className="form-header">
                <Plus size={28} />
                <div>
                  <h2>Post a Ride</h2>
                  <p>Share your journey with passengers</p>
                </div>
              </div>
              <div className="form-box">
                <h3><MapPin size={18} /> Route Details</h3>
                <div className="form-row">
                  <div className="form-group">
                    <label>From (Source)</label>
                    <input
                      type="text"
                      placeholder="e.g., Chennai"
                      value={form.source}
                      onChange={(e) => setForm({ ...form, source: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>To (Destination)</label>
                    <input
                      type="text"
                      placeholder="e.g., Bangalore"
                      value={form.destination}
                      onChange={(e) => setForm({ ...form, destination: e.target.value })}
                    />
                  </div>
                </div>

                <h3><Calendar size={18} /> Schedule</h3>
                <div className="form-row">
                  <div className="form-group">
                    <label>Date & Time</label>
                    <DatePicker
                      selected={selectedDate}
                      onChange={(date) => setSelectedDate(date)}
                      showTimeSelect
                      dateFormat="dd/MM/yyyy h:mm aa"
                      className="date-input"
                      minDate={new Date()}
                    />
                  </div>
                  <div className="form-group">
                    <label>Available Seats</label>
                    <input
                      type="number"
                      placeholder="e.g., 3"
                      min="1"
                      max="10"
                      value={form.availableSeats}
                      onChange={(e) => setForm({ ...form, availableSeats: e.target.value })}
                    />
                  </div>
                </div>

                <h3><Wallet size={18} /> Pricing </h3>
                <div className="form-row">
                  <div className="form-group">
                    <label>Price per Seat (₹)</label>
                    <input
                      type="number"
                      placeholder="e.g., 500"
                      min="0"
                      value={form.pricePerSeat}
                      onChange={(e) => setForm({ ...form, pricePerSeat: e.target.value })}
                    />
                  </div>
                </div>

                <h3><Car size={18} /> Vehicle Details</h3>
                <div className="form-row">
                  <div className="form-group">
                    <label>Vehicle Model</label>
                    <input
                      type="text"
                      placeholder="e.g., Swift Dzire"
                      value={vehicleForm.vehicleModel}
                      onChange={(e) => setVehicleForm({ ...vehicleForm, vehicleModel: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>License Plate</label>
                    <input
                      type="text"
                      placeholder="e.g., TN 01 AB 1234"
                      value={vehicleForm.licensePlate}
                      onChange={(e) => setVehicleForm({ ...vehicleForm, licensePlate: e.target.value })}
                    />
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '15px' }}>
                  <button onClick={saveVehicleDetails} className="btn-secondary" style={{ flex: 1 }}>
                    <Check size={18} /> Save Vehicle
                  </button>
                  <button onClick={postRide} className="btn-post" style={{ flex: 1 }}>
                    <Plus size={18} /> Post Ride
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Driver - My Rides */}
        {activeTab === 'myrides' && userType === 'driver' && (
          <div className="tab-panel">
            <div className="list-section">
              <div className="list-header">
                <Car size={28} />
                <div>
                  <h2>My Rides</h2>
                  <p>View all your posted rides</p>
                </div>
              </div>
              <div className="rides-grid">
                {myRides.length > 0 ? (
                  myRides.map(ride => (
                    <div key={ride.id} className="ride-card">
                      <div className="ride-header">
                        <div className="ride-route-display">
                          <div className="route-point">
                            <div className="route-dot"></div>
                            <span>{ride.source}</span>
                          </div>
                          <div className="route-line"></div>
                          <div className="route-point">
                            <div className="route-dot destination"></div>
                            <span>{ride.destination}</span>
                          </div>
                        </div>
                        <button
                          onClick={() => setShowDeleteConfirm(ride.id)}
                          className="btn-delete-icon"
                          title="Delete ride"
                        >
                          <X size={18} />
                        </button>
                      </div>
                      <div className="ride-details">
                        <div className="detail-item">
                          <Calendar size={16} />
                          <span>{formatDate(ride.dateTime)}</span>
                        </div>
                        <div className="detail-item">
                          <Users size={16} />
                          <span>{ride.availableSeats} seats</span>
                        </div>
                        {ride.pricePerSeat > 0 && (
                          <div className="detail-item price">
                            <Wallet size={16} />
                            <span>₹{ride.pricePerSeat}/seat</span>
                          </div>
                        )}
                      </div>
                      <div className="ride-status">
                        <span className={`status-badge ${ride.status || 'active'}`}>
                          {ride.status || 'Active'}
                        </span>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="empty-state">
                    <Car size={64} />
                    <h3>No Rides Posted</h3>
                    <p>Start by posting a ride for passengers</p>
                    <button onClick={() => setActiveTab('post')} className="btn-primary">
                      <Plus size={18} /> Post a Ride
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
        {/* Passenger Home - Find Rides */}
        {activeTab === 'home' && userType === 'passenger' && (
          <div className="tab-panel">
            <div className="search-section">
              <div className="search-header">
                <Search size={28} />
                <div>
                  <h2>Find a Ride</h2>
                  <p>Search for rides going your way</p>
                </div>
              </div>
              <div className="search-box">
                <div className="form-row">
                  <div className="form-group">
                    <label><MapPin size={18} /> From</label>
                    <input
                      type="text"
                      placeholder="e.g., Chennai"
                      value={searchForm.source}
                      onChange={(e) => setSearchForm({ ...searchForm, source: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label><MapPin size={18} /> To</label>
                    <input
                      type="text"
                      placeholder="e.g., Bangalore"
                      value={searchForm.destination}
                      onChange={(e) => setSearchForm({ ...searchForm, destination: e.target.value })}
                    />
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label><Calendar size={18} /> Date (Optional)</label>
                    <DatePicker
                      selected={searchForm.date ? new Date(searchForm.date) : null}
                      onChange={(date) => setSearchForm({ ...searchForm, date: date ? date.toISOString().split('T')[0] : '' })}
                      dateFormat="dd/MM/yyyy"
                      className="date-input"
                      placeholderText="Select date"
                      minDate={new Date()}
                    />
                  </div>
                  <div className="form-group">
                    <label><Users size={18} /> Seats (Optional)</label>
                    <input
                      type="number"
                      placeholder="No. of seats"
                      min="1"
                      max="10"
                      value={searchForm.seats || ''}
                      onChange={(e) => setSearchForm({ ...searchForm, seats: e.target.value })}
                    />
                  </div>
                </div>

                <button onClick={fetchRides} className="btn-search">
                  <Search size={20} /> Search Rides
                </button>
              </div>
            </div>

            {/* Show search results */}
            {rides.length > 0 && (
              <div className="results-section">
                <h3><Car size={20} /> Available Rides ({rides.length})</h3>
                <div className="rides-grid">
                  {rides.map(ride => (
                    <div key={ride.id} className="ride-card">
                      <div className="ride-header">
                        <div className="ride-route-display">
                          <div className="route-point">
                            <div className="route-dot"></div>
                            <span>{ride.source}</span>
                          </div>
                          <div className="route-line"></div>
                          <div className="route-point">
                            <div className="route-dot destination"></div>
                            <span>{ride.destination}</span>
                          </div>
                        </div>
                      </div>
                      <div className="ride-details">
                        <div className="detail-item">
                          <Calendar size={16} />
                          <span>{formatDate(ride.dateTime)}</span>
                        </div>
                        <div className="detail-item">
                          <Users size={16} />
                          <span>{ride.availableSeats} seat{ride.availableSeats !== 1 ? 's' : ''} available</span>
                        </div>
                        <div className="detail-item">
                          <Car size={16} />
                          <span>{ride.vehicleModel || 'Not specified'}</span>
                        </div>
                        {ride.pricePerSeat > 0 && (
                          <div className="detail-item price">
                            <Wallet size={16} />
                            <span>₹{ride.pricePerSeat}/seat</span>
                          </div>
                        )}
                      </div>
                      <div className="seat-selector">
                        <label>Number of seats:</label>
                        <div className="seat-controls">
                          <button
                            onClick={() => setSelectedSeats(prev => ({ ...prev, [ride.id]: Math.max(1, (prev[ride.id] || 1) - 1) }))}
                            className="seat-btn"
                          >-</button>
                          <span className="seat-count">{selectedSeats[ride.id] || 1}</span>
                          <button
                            onClick={() => setSelectedSeats(prev => ({ ...prev, [ride.id]: Math.min(ride.availableSeats, (prev[ride.id] || 1) + 1) }))}
                            className="seat-btn"
                            disabled={(selectedSeats[ride.id] || 1) >= ride.availableSeats}
                          >+</button>
                        </div>
                      </div>
                      <button
                        onClick={() => bookRide(ride.id)}
                        className="btn-book"
                        disabled={ride.availableSeats < 1}
                      >
                        <Check size={18} />
                        Book {selectedSeats[ride.id] || 1} seat{(selectedSeats[ride.id] || 1) > 1 ? 's' : ''}
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Empty State - No rides found */}
            {rides.length === 0 && searchForm.source && (
              <div className="empty-state">
                <Search size={64} />
                <h3>No rides available</h3>
                <p>Try searching with different locations or check back later</p>
              </div>
            )}
          </div>
        )}

        {/* Passenger - My Bookings */}
        {activeTab === 'bookings' && userType === 'passenger' && (
          <div className="tab-panel">
            <div className="list-section">
              <div className="list-header">
                <Ticket size={28} />
                <div>
                  <h2>My Bookings</h2>
                  <p>Manage your ride bookings</p>
                </div>
              </div>
              <div className="rides-grid">
                {myBookings.length > 0 ? (
                  myBookings.map(booking => (
                    <div key={booking.id} className="ride-card">
                      <div className="ride-header">
                        <div className="ride-route-display">
                          <div className="route-point">
                            <div className="route-dot"></div>
                            <span>{booking.ride?.source || 'N/A'}</span>
                          </div>
                          <div className="route-line"></div>
                          <div className="route-point">
                            <div className="route-dot destination"></div>
                            <span>{booking.ride?.destination || 'N/A'}</span>
                          </div>
                        </div>
                        {booking.status !== 'cancelled' && booking.status !== 'rejected' && (
                          <button
                            onClick={() => setShowCancelConfirm(booking.id)}
                            className="btn-delete-icon"
                            title="Cancel booking"
                          >
                            <X size={18} />
                          </button>
                        )}
                      </div>
                      <div className="ride-details">
                        <div className="detail-item">
                          <Calendar size={16} />
                          <span>{formatDate(booking.ride?.dateTime)}</span>
                        </div>
                        <div className="detail-item">
                          <Car size={16} />
                          <span>{booking.ride?.vehicleModel || 'Not specified'}</span>
                        </div>
                        <div className="detail-item">
                          <Users size={16} />
                          <span>{booking.seatsBooked} seat{booking.seatsBooked !== 1 ? 's' : ''}</span>
                        </div>
                        <div className="detail-item price">
                          <Wallet size={16} />
                          <span>₹{booking.ride?.pricePerSeat || 0}/seat</span>
                        </div>
                      </div>
                      <div className="booking-status">
                        <span className={`status-badge ${
                          booking.status === 'accepted' ? 'active' :
                          booking.status === 'pending' ? 'pending' :
                          booking.status === 'cancelled' ? 'cancelled' :
                          booking.status === 'rejected' ? 'cancelled' : 'completed'
                        }`}>
                          {booking.status === 'accepted' ? '✅ Accepted' :
                           booking.status === 'rejected' ? '❌ Rejected' :
                           booking.status === 'pending' ? '⏳ Pending' :
                           booking.status === 'cancelled' ? '❌ Cancelled' : '✅ Confirmed'}
                        </span>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="empty-state">
                    <Ticket size={64} />
                    <h3>No Bookings Yet</h3>
                    <p>Book a ride to get started</p>
                    <button onClick={() => setActiveTab('home')} className="btn-primary">
                      <Search size={18} /> Find Rides
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
// ==================== APP COMPONENT ====================
function App() {
  const [token, setToken] = useState(null);
  const [user, setUser] = useState(null);

  useEffect(() => {
    const savedToken = localStorage.getItem('token');
    const savedUser = localStorage.getItem('user');
    if (savedToken && savedUser && savedUser !== 'undefined') {
      try {
        setToken(savedToken);
        setUser(JSON.parse(savedUser));
      } catch (e) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
      }
    }
  }, []);

  return (
    <Router>
      <Routes>
        <Route path="/" element={token && user ? <Navigate to="/dashboard" /> : <Auth setToken={setToken} setUser={setUser} />} />
        <Route path="/dashboard" element={token && user ? <Dashboard token={token} user={user} setToken={setToken} setUser={setUser} /> : <Navigate to="/" />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  );
}

export default App;
