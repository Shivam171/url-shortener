# 🚀 Industry-Level URL Shortener

An advanced, production-grade URL shortening service built for internet-scale systems. Designed with scalability, observability, and extensibility in mind — yet still under active development with new features being added regularly.

> ⚠️ **Note:** This project is still in development. More features and improvements are being added continuously. Stay tuned!

---

## 📌 What is a URL Shortener?

A **URL shortener** transforms long URLs into short, manageable links that are easier to share and track. For example:

```
Original: https://example.com/urls/www.google.com/about/ndksnadkdasd..  
Shortened: https://yourshorturl.com/xYz1K8
```

When the short URL is accessed, the service **redirects** the user to the original long URL — possibly validating, tracking, and applying security or expiration rules in between.

---

## ⚙️ Scale Requirements

This URL shortener is built with large-scale production usage in mind:

* 🔥 **100M Daily Active Users**
* 📈 **Read\:Write ratio** of **100:1**
* 🧠 **Data retention**: 5 years
* 🧲 **1M write requests/day**
* 📊 Optimized for heavy **read traffic** and long-term analytics

---

## 🌟 Features

### ✅ Core Functionality

1. 🔗 Long URL shortening
2. ↪️ Short URL redirection
3. 🏷️ Custom aliases for URLs
4. 📷 QR code generation
5. ⏳ URL expiration (time-based and click-based)
6. ✅ URL validation and reachability checks
7. 🔐 Password-protected short URLs
8. 🍪 Cookies-based session management and Remember Me
8. 📊 Click-based expiration
9. 📣 Social media share tracking
10. 🧠 Link versioning for A/B testing and rollback
11. 📊 Analytics (via Kafka):
    * Click counts
    * Device, browser, OS and other info
    * Geographic location
12. 🌸 **Bloom Filter** for fast duplicate checks
13. ✨ **Redis caching** for lightning-fast lookups
14. 🧱 **PostgreSQL** as the persistent backend
15. 🧪 Server-side rendering with HTML, Tailwind, JavaScript + Thymeleaf for user-facing pages (like password entry)

---

## 🧠 Design Decisions & Architecture

### 🔢 Unique ID Generation

To ensure **distributed-safe short codes**, the system uses:

* ❄️ **Snowflake IDs** (timestamp + machine ID)
* Encoded via **Base62** (A–Z, a–z, 0–9) for shorter, URL-friendly codes
* ❌ Avoided manually configured machine IDs via `application.yml` to reduce human error

### ↺ Caching Strategy

* Uses a **multi-layered approach**:

    * **Bloom Filter** (existence check)
    * **Redis** (hot read/write cache)
    * **PostgreSQL** (source of truth)

> ⚠️ In future iterations, we can replace PostgreSQL with **Cassandra** or **DynamoDB** for true horizontal scalability.

---

## 🛠️ Tech Stack

| Layer               | Tech                             |
| ------------------- |----------------------------------|
| **Backend**         | Java 21, Spring Boot             |
| **Async Messaging** | Apache Kafka, Kafdrop            |
| **Database**        | PostgreSQL                       |
| **Cache**           | Redis                            |
| **Bloom Filter**    | Guava or Custom In-memory Filter |
| **Frontend**        | Thymeleaf + HTML/CSS/JS          |
| **Build Tool**      | Maven or Gradle                  |

---

## 📚 How to Run

> Prerequisites:
> * Java 17+
> * PostgreSQL
> * Redis
> * Kafka & Zookeeper
> * Maven or Gradle

```bash
# Clone the project
git clone https://github.com/Shivam171/url-shortner.git
cd url-shortener

# Configure application.yml (DB, Redis, Kafka settings)

# Run the app
./mvnw spring-boot:run
```

---

## 📬 Contributions

PRs, suggestions, and feature requests are welcome. Feel free to open issues or contribute with enhancements.

---

## 🔖 License

[MIT License](https://github.com/Shivam171/url-shortener/blob/main/LICENSE) – use it freely, with credit.

---

## 🙌 Stay Updated

I'm actively building and improving this system every week. You can follow along or contribute — let’s make a truly scalable URL shortener together.

> *— Built for scale. Developed with passion.*
