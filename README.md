# Trading-Platform
A feature-rich trading platform designed to handle real-time cryptocurrency transactions and provide users with an intuitive interface to track, analyze, and manage their investments. This project integrates a secure backend with a dynamic frontend to deliver seamless trading experiences.

---

## Features

### Backend
- **High-Performance Processing**: Capable of handling 10,000+ transactions efficiently using Spring Boot and PostgreSQL.
- **Secure RESTful APIs**:
  - Buy and sell cryptocurrency.
  - Wallet-to-wallet transfers.
  - Deposit and withdraw funds.
- **Authentication**: JWT-based authentication ensures secure access control and data protection.

### Frontend
- **Real-Time Data Tracking**: Monitor and analyze trends for 10,000+ cryptocurrencies.
- **Personalized Watchlists**: Users can create and manage custom watchlists for their favorite coins.
- **Interactive Visualizations**: Responsive charts and graphs enable better decision-making.
- **Modern UI**: Built with React and Tailwind CSS for a clean, user-friendly experience.

---

## Tech Stack

### Backend
- **Framework**: Spring Boot
- **Database**: PostgreSQL
- **ORM**: JPA (Java Persistence API)
- **Authentication**: JWT (JSON Web Token)

### Frontend
- **Framework**: React
- **Styling**: Tailwind CSS

---

## Installation & Setup

### Prerequisites
- Java 11 or later
- Node.js and npm
- PostgreSQL

### Backend Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/NikitS04/Crypto-Platform.git
   cd Crypto-Platform
   ```
2. Configure the database in `application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/trading_platform
   spring.datasource.username=<your_username>
   spring.datasource.password=<your_password>
   ```
3. Build and run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

### Frontend Setup
1. Navigate to the frontend directory:
   ```bash
   cd Crypto-Platform
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm start
   ```

---

## Usage
1. Access the application at `http://localhost:3000`.
2. Sign in or create an account.
3. Use the dashboard to:
   - Track cryptocurrency prices and trends.
   - Manage your personalized watchlist.
   - Buy, sell, deposit, or withdraw funds securely.

---

## Acknowledgments
- Built using **Spring Boot** and **React**.
- Styled with **Tailwind CSS** for a modern and responsive design.
- Data persistence and management powered by **PostgreSQL**.

---

Feel free to reach out with questions, suggestions, or issues through the repository's [Issues](https://github.com/NikitS04/Crypto-Platform/issues) section!

