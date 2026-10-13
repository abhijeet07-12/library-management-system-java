Library Management System
A web-based Library Management System built using Java, Spring Boot, HTML, CSS, and JavaScript.

✨ Features
➕ Add new books
📖 View all available books
🔍 Search books by title or author
🗑️ Delete books
🔄 Refresh the book list
🌐 Interactive and user-friendly web interface

🛠️ Technologies Used
Java
Spring Boot
HTML5
CSS3
JavaScript
Maven
Git and GitHub

📂 Project Structure
library-management-system/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       └── static/
│   │           └── index.html
│   └── test/
├── .mvn/
├── mvnw
├── mvnw.cmd
└── pom.xml

🚀 How to Run
Install Java JDK.
Clone or download this repository.
Open the project in IntelliJ IDEA.
Wait for Maven to download dependencies.
Run LibraryManagementApplication.java.
Open your browser and visit http://localhost:8080.

🎯 Project Objective
This project demonstrates CRUD operations, REST API development, and integration between a Spring Boot backend and a web frontend.

👨‍💻 Author
Omraje Mane

GitHub: @omrajemane437-droid
Repository: Library Management System

## 📸 Application Screenshots

### All Books

![Library Management System - All Books](Screenshot%202026-10-11%20024114.png)

### Search Books

![Library Management System - Search](Screenshot%202026-10-11%20024127.png)

### Add a New Book

![Library Management System - Add Book](Screenshot%202026-10-11%20024105.png)

## Database: MySQL

The app now stores everything in MySQL (database `library_management`). H2 is used only by the automated tests (in-memory).

1. Create the database (MySQL Workbench or `mysql -u root -p`):
   ```sql
   CREATE DATABASE library_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Set environment variables (never put the password in a file that is committed):
   - `DB_PASSWORD` (required), `DB_USERNAME` (default `root`), `DB_URL` (default `jdbc:mysql://localhost:3306/library_management?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`)
   - Windows PowerShell, current terminal only: `$env:DB_PASSWORD="your-password"`
   - Windows permanent: `setx DB_PASSWORD "your-password"`, then restart VS Code.
3. Run: `.\mvnw.cmd spring-boot:run` (tables are created on first start; `ddl-auto=update` never deletes data).
4. Verify persistence: add a book, stop the app, start it again, confirm the book is still listed (or `SELECT * FROM library_management.book;`).

Keep MySQL bound to localhost (`bind-address=127.0.0.1`, the default) and do not open port 3306 in the firewall.

### Moving old H2 data (./data/librarydb.mv.db)
Back up first: copy the `data` folder to `data-backup`. Start the app once against MySQL so the tables exist, then for each table (`book`, `library_member`, `book_loan`, `app_setting`) export from the H2 console (`jdbc:h2:file:./data/librarydb`, user `sa`, empty password; run it from a temporary H2 console, as the app no longer ships one) with `CALL CSVWRITE('book.csv', 'SELECT * FROM BOOK');` and import into MySQL with Workbench's Table Data Import Wizard. Import in this order: book, library_member, app_setting, book_loan. Timestamps are stored as UTC instants; check that due/return dates look right after import.
