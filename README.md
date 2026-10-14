# 🌱 EnviroGuard AI 

**Enterprise Factory Emissions Monitoring & AI Compliance Audit System**

EnviroGuard AI is a full-stack enterprise application designed to monitor factory sensor telemetry (NOx, CO, SO2) in real-time and provide AI-driven compliance audits. It empowers factory administrators to ensure environmental regulations are met while giving workers visibility into live system statuses.

## 🚀 Features

- **Live Telemetry Dashboard:** Real-time visualization of sensor data (NOx, CO, SO2) using WebSockets and Recharts.
- **AI Compliance Audits:** Generate instant AI reports to analyze sensor data against strict environmental compliance limits and suggest mitigation actions.
- **Role-Based Access Control (RBAC):** Secure JWT authentication separating Factory Administrators (who can execute audits and create users) and Workers (who monitor feeds).
- **Data Export:** Easily download historical telemetry data as a CSV for external analysis.
- **Cloud Ready:** Fully configured for deployment on modern cloud platforms (Vercel for Frontend, Render/Railway for Backend).

## 💻 Tech Stack

**Frontend**
* React.js (Vite)
* Recharts (Data Visualization)
* SockJS & STOMP (Real-time WebSockets)
* Vercel (Hosting)

**Backend**
* Java & Spring Boot
* Spring Security (JWT Authentication)
* Spring Data JPA / Hibernate
* PostgreSQL (Neon DB)
* Render / Railway (Hosting)

## 🛠️ Getting Started

### Prerequisites
- Node.js & npm (for Frontend)
- Java 17+ & Maven (for Backend)
- PostgreSQL database (e.g., Neon)

### Environment Variables
Create a `.env` file or configure your deployment environment with the following keys:

**Backend (`application.properties` or System Env Vars):**
```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://<your-db-url>:5432/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=<your_db_username>
SPRING_DATASOURCE_PASSWORD=<your_db_password>
SPRING_JPA_HIBERNATE_DDL_AUTO=update
JWT_SECRET=<your_secure_jwt_secret_key>
