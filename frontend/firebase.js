import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { connectAuthEmulator } from 'firebase/auth';

const firebaseConfig = {
  apiKey: "AIzaSyDdGZzmFHTa6RQBzi4YWDcRuoqwMUkI9b4",
  authDomain: "ride-sharing-cf927.firebaseapp.com",
  projectId: "ride-sharing-cf927",
  storageBucket: "ride-sharing-cf927.firebasestorage.app",
  messagingSenderId: "290805289269",
  appId: "1:290805289269:web:be6a95ff7c9936c6c03c7c",
  measurementId: "G-0PZ2E0ZC8D"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);