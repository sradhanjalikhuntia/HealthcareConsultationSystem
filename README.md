# 🏥 ApexCare — Enterprise Healthcare & Hospital Management Platform

ApexCare is a modern, enterprise-grade Hospital Management System built with a scalable **Microservices Architecture** using **Spring Cloud, Spring Boot 3, PostgreSQL**, and a responsive **React (Vite + TypeScript + Tailwind CSS)** web portal.

---

## 🏗️ Architecture Overview

The backend is decomposed into independent, domain-driven microservices managed via Eureka Service Discovery and an API Gateway:

| Service | Port | Description |
|---|---|---|
| **Eureka Server** | `8761` | Service Registry & Discovery |
| **API Gateway** | `8085` | Centralized Routing, Security, CORS |
| **Auth Service** | `8081` | JWT Auth, Role-Based Access Control, Email OTP Verification, Admin & Staff Approval |
| **Patient Service** | `8082` | Electronic Medical Records (EMR), Inpatient Ward & Bed Management |
| **Appointment Service** | `8083` | Consultation Booking, Doctor Scheduling, Queue Tracking |
| **Billing Service** | `8084` | Invoicing, Payment Tracking, Financial & Demographic Analytics |
| **Frontend Web App** | `5173` | React 18 + TypeScript + Vite + Tailwind CSS |

---

## ✨ Key Features & Workflows

### 1. 🔐 Security & Role-Based Access Control (RBAC)
- **Roles:** `ADMIN`, `DOCTOR`, `NURSE`, `BILLING_ADMIN`, `RECEPTIONIST`, `PATIENT`.
- **Admin Registration Security:** Registration as `ADMIN` requires a secret administrative passcode (`Abhi`).
- **Staff Approval Workflow:** Self-registered staff accounts (`DOCTOR`, `NURSE`, `BILLING_ADMIN`, `RECEPTIONIST`) are created in a pending state and must be verified and approved by an Administrator before they can sign in.
- **Patient Self-Service:** Patients enjoy instant registration with secure email OTP verification.

### 2. 🏥 Clinical & Ward Operations
- **Doctor Consultation & Queue:** Real-time patient appointment management, consultation notes, prescriptions, and status tracking.
- **Nurse Ward & Bed Dashboard:** Bed occupancy monitoring, automated cleaning queue status (`CLEANING`), and single-click `Mark Bed Ready` for fast turnaround.
- **Active / Inactive Patient Lifecycle:** Complete patient record reactivation and lifecycle management.

### 3. 💳 Billing & Analytics
- Automated bill generation for consultations and inpatient admissions.
- Real-time financial metrics (Settled vs Pending invoices).
- Patient age demographic visualizations and billing analytics.

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+**
- **Node.js 18+** & npm
- **PostgreSQL 14+** (configured on port `5433` or via Docker)
- **Maven 3.8+**

### Backend Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/ABHIRANJAN-KUMAR1/ApexCare.git
   cd ApexCare
   ```

2. Configure PostgreSQL databases:
   - `auth_db`, `patient_db`, `appointment_db`, `billing_db`

3. Build all microservices:
   ```bash
   mvn clean package -DskipTests
   ```

4. Start services in order:
   - Eureka Server (`8761`)
   - API Gateway (`8085`)
   - Auth Service (`8081`)
   - Patient Service (`8082`)
   - Appointment Service (`8083`)
   - Billing Service (`8084`)

### Frontend Setup
1. Navigate to the frontend directory:
   ```bash
   cd frontend
   npm install
   ```

2. Start the development server:
   ```bash
   npm run dev
   ```
3. Open `http://localhost:5173` in your browser.

---

## 🐳 Docker Deployment
You can also launch the full stack with Docker Compose:
```bash
docker-compose up --build -d
```

---

## 📄 License
This project is licensed under the MIT License.

