# Atlas CMMS — User Manual

> **Version:** 1.0  
> **Last Updated:** March 5, 2026

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Getting Started](#2-getting-started)
3. [Dashboard & Navigation](#3-dashboard--navigation)
4. [Work Orders](#4-work-orders)
5. [Preventive Maintenance](#5-preventive-maintenance)
6. [Work Requests](#6-work-requests)
7. [Assets](#7-assets)
8. [Locations](#8-locations)
9. [Inventory (Parts & Sets)](#9-inventory-parts--sets)
10. [Purchase Orders](#10-purchase-orders)
11. [Meters & Readings](#11-meters--readings)
12. [People & Teams](#12-people--teams)
13. [Vendors & Customers](#13-vendors--customers)
14. [Files](#14-files)
15. [Analytics](#15-analytics)
16. [Settings & Configuration](#16-settings--configuration)
17. [Import & Export](#17-import--export)
18. [Localization](#18-localization)
19. [Administrator Guide](#19-administrator-guide)
20. [Troubleshooting](#20-troubleshooting)

---

## 1. Introduction

**Atlas CMMS** is a Computerized Maintenance Management System designed to help organizations manage their maintenance operations, track assets, schedule preventive maintenance, handle work orders, manage inventory, and generate analytics reports.

### Key Capabilities

- **Work Order Management** — Create, assign, track, and close maintenance work orders
- **Preventive Maintenance** — Schedule recurring maintenance based on time or meter readings
- **Asset Management** — Track equipment with hierarchical relationships and full lifecycle data
- **Inventory Management** — Manage spare parts, track quantities, and automate reordering
- **Request System** — Allow non-maintenance staff to submit work requests for approval
- **Analytics & Reporting** — Dashboards for work order, asset, part, and request analytics
- **Multi-language Support** — Available in 14 languages
- **Role-Based Access** — Granular permissions per user role

---

## 2. Getting Started

### 2.1 Accessing the System

Open your web browser and navigate to the application URL:

- **Local Development:** `http://localhost:3000`
- **Production:** Your organization's configured URL (e.g., `https://maint.yourdomain.com`)

### 2.2 Creating an Account

1. Click **Sign Up** on the login page.
2. Fill in your details: name, email, password, and company name.
3. If email verification is enabled, check your inbox and click the activation link.
4. Log in with your credentials.

### 2.3 Logging In

1. Enter your **email** and **password**.
2. Click **Sign In**.
3. If SSO is enabled, you can also sign in with **Google** or **Microsoft**.

### 2.4 Accepting an Invitation

If you were invited by an administrator:

1. Open the invitation link from your email.
2. Set your password.
3. Log in to access the system with your assigned role.

### 2.5 Password Recovery

1. On the login page, click **Forgot Password?**
2. Enter your registered email address.
3. Check your inbox for a reset link.
4. Click the link and set a new password.

---

## 3. Dashboard & Navigation

### Sidebar Navigation

After logging in, the application presents a sidebar on the left with the following sections:

| Menu Item | Description |
|-----------|-------------|
| **Work Orders** | View and manage all work orders |
| **Preventive Maintenance** | Manage PM schedules |
| **Requests** | View and approve work requests |
| **Assets** | Browse and manage assets |
| **Locations** | Manage location hierarchy |
| **Inventory** | Parts and part sets |
| **Purchase Orders** | Manage procurement |
| **Meters** | Track equipment meters |
| **People & Teams** | Manage users and teams |
| **Vendors & Customers** | External contacts |
| **Categories** | Manage classification categories |
| **Files** | Uploaded documents and images |
| **Analytics** | Data dashboards and charts |
| **Settings** | System configuration |

The sidebar can be collapsed for more screen space by clicking the toggle button.

---

## 4. Work Orders

Work orders are the core of Atlas CMMS. They represent individual maintenance tasks that need to be completed.

### 4.1 Viewing Work Orders

- Navigate to **Work Orders** from the sidebar.
- The list view shows all work orders with columns for title, status, priority, assigned user, asset, due date, and more.
- Use the **search** bar and **column filters** to find specific work orders.
- Click any row to view full details.

### 4.2 Creating a Work Order

1. Click the **+ (Add)** button.
2. Fill in the required fields:
   - **Title** — Brief description of the work
   - **Priority** — None, Low, Medium, High
   - **Status** — Open (default)
   - **Assigned To** — User or Team
   - **Asset** — Select the related asset (optional)
   - **Location** — Select the location (optional)
   - **Due Date** — When the work should be completed
   - **Description** — Detailed instructions
   - **Category** — Work order category
3. Optionally add:
   - **Checklist tasks** — Step-by-step task list
   - **Files / Images** — Attach documents or photos
4. Click **Save**.

### 4.3 Managing a Work Order

| Action | How |
|--------|-----|
| **Change status** | Use the status dropdown (Open → In Progress → On Hold → Complete) |
| **Add labor** | Go to the Labor tab, log hours and hourly rate |
| **Add parts used** | Go to the Parts tab, select parts and quantities |
| **Add costs** | Go to the Additional Costs tab, enter extra expenses |
| **Add tasks** | Go to the Tasks tab, create or check off items |
| **Attach files** | Go to the Files tab, upload documents or photos |
| **View history** | Open the History tab to see all changes made |

### 4.4 Work Order Statuses

| Status | Meaning |
|--------|---------|
| **Open** | Newly created, not yet started |
| **In Progress** | Work is actively being performed |
| **On Hold** | Paused (e.g., waiting for parts) |
| **Complete** | Work finished |

### 4.5 Calendar View

Work orders can be displayed in a calendar view showing due dates and schedules. Switch between day, week, and month views.

---

## 5. Preventive Maintenance

Preventive Maintenance (PM) allows you to schedule recurring maintenance tasks that automatically generate work orders.

### 5.1 Creating a PM Schedule

1. Navigate to **Preventive Maintenance**.
2. Click **+ (Add)**.
3. Fill in:
   - **Title** — Name of the PM task
   - **Asset** / **Location** — What to maintain
   - **Assigned To** — User or Team
   - **Schedule** — Frequency (daily, weekly, monthly, yearly, or custom cron)
   - **Start Date** — When to begin scheduling
   - **Tasks** — Checklist of steps to perform
4. Click **Save**.

### 5.2 How It Works

- The system automatically creates work orders based on the PM schedule.
- Notifications are sent to assigned users before the due date.
- Meter-based triggers can also create work orders when a meter reading exceeds a threshold.

### 5.3 Viewing Recent Work Orders

Each PM entry shows its recently generated work orders so you can track completion rates.

---

## 6. Work Requests

Requests allow non-maintenance personnel to submit work that needs to be done, which maintenance managers can then approve or reject.

### 6.1 Submitting a Request

1. Navigate to **Requests**.
2. Click **+ (Add)**.
3. Fill in the title, description, priority, asset, and location.
4. Click **Submit**.

### 6.2 Approving / Rejecting Requests

Managers see pending requests and can:

- **Approve** — Automatically creates a work order from the request.
- **Cancel** — Rejects the request with an optional reason.

---

## 7. Assets

Assets represent the physical equipment and machinery that your organization maintains.

### 7.1 Viewing Assets

- Navigate to **Assets** from the sidebar.
- Assets are displayed in a list or grouped view.
- Click an asset to see its detail page.

### 7.2 Asset Detail Tabs

| Tab | Content |
|-----|---------|
| **Details** | Name, description, model, serial number, category, location, parent asset, custom fields |
| **Work Orders** | All work orders linked to this asset |
| **Parts** | Parts associated with this asset |
| **Files** | Documents, manuals, and photos |
| **Meters** | Meters attached to this asset with readings |
| **Downtimes** | Downtime history and duration |
| **Analytics** | Asset-specific cost and reliability analytics |

### 7.3 Creating an Asset

1. Click **+ (Add)**.
2. Fill in name, description, location, category, and other fields.
3. Optionally set a **parent asset** to create a hierarchy.
4. Attach files (manuals, photos).
5. Click **Save**.

### 7.4 Asset Lookup

Assets support **NFC tag** and **barcode** lookup for quick identification in the field.

---

## 8. Locations

Locations represent the physical places where assets are installed and work is performed.

### 8.1 Location Hierarchy

Locations support a parent-child hierarchy (e.g., Building → Floor → Room). Use **Children** to navigate through the tree.

### 8.2 Creating a Location

1. Navigate to **Locations**.
2. Click **+ (Add)**.
3. Enter name, description, address, and optionally select a parent location.
4. Upload a **floor plan** image (optional).
5. Click **Save**.

---

## 9. Inventory (Parts & Sets)

### 9.1 Parts

Parts are spare parts and materials used in maintenance work.

- **View parts** — Navigate to **Inventory → Parts**.
- **Create a part** — Add name, description, quantity, cost, minimum quantity, category, vendors, and storage location.
- **Track usage** — Part quantities are automatically decremented when used in work orders.
- **Low stock alerts** — Set minimum quantities to get notified when stock is low.

### 9.2 Part Sets

Part sets are predefined groups of parts commonly used together, making it easy to add multiple parts to a work order at once.

---

## 10. Purchase Orders

Purchase orders help you procure parts and materials from vendors.

### 10.1 Creating a Purchase Order

1. Navigate to **Purchase Orders**.
2. Click **+ (Add)** or **Create**.
3. Select a **vendor**.
4. Add **parts and quantities**.
5. Set the due date and any notes.
6. Click **Save**.

### 10.2 Responding to a Purchase Order

Vendors can be notified and the PO status updated as:
- **Draft** → **Pending** → **Approved** → **Received**

When parts are received, inventory quantities can be updated.

---

## 11. Meters & Readings

Meters track measurable values on equipment (e.g., operating hours, mileage, temperature).

### 11.1 Creating a Meter

1. Navigate to **Meters**.
2. Click **+ (Add)**.
3. Set the name, unit, asset, category, and reading frequency.
4. Click **Save**.

### 11.2 Logging Readings

1. Open a meter.
2. Enter a new reading value with the date.
3. Click **Save**.

### 11.3 Meter-Based Triggers

Create triggers that automatically generate work orders when a meter reading reaches a specified threshold. This is useful for usage-based maintenance (e.g., oil change every 5,000 hours).

---

## 12. People & Teams

### 12.1 Managing Users

- Navigate to **People & Teams → People**.
- **Invite users** — Send email invitations with an assigned role.
- **Edit users** — Update name, role, phone, and other details.
- **Disable users** — Deactivate without deleting.

### 12.2 Managing Teams

- **Create teams** — Group users into teams (e.g., "Electrical Team").
- **Assign to work orders** — Teams can be assigned to work orders alongside or instead of individuals.

### 12.3 Roles & Permissions

Each user is assigned a **role** that controls what they can see and do:

| Permission Type | Description |
|----------------|-------------|
| **View** | Can see records (own only or all) |
| **Create** | Can create new records |
| **Edit Others** | Can modify records created by other users |
| **Delete Others** | Can remove records created by other users |

Permissions are set per entity type (work orders, assets, parts, etc.).

---

## 13. Vendors & Customers

### Vendors
Manage external vendors who supply parts or services. Each vendor can have contact information, a company name, and be linked to purchase orders and parts.

### Customers
Track customers whose assets your organization maintains. Customers can be linked to assets and locations.

---

## 14. Files

The **Files** section provides a centralized file manager for all uploaded documents, images, and attachments.

- Files are stored in **MinIO** (S3-compatible storage) by default.
- Each file is scoped to your company.
- Files can be associated with work orders, assets, parts, and other entities.
- Supported operations: upload, rename, download, and delete.

---

## 15. Analytics

Atlas CMMS provides analytics dashboards for data-driven decision making.

### Available Dashboards

| Dashboard | Key Metrics |
|-----------|------------|
| **Work Order Analytics** | Completion rate, average time to complete, status distribution, cost breakdown, trends over time |
| **Asset Analytics** | Downtime analysis, cost per asset, reliability metrics, mean time between failures (MTBF) |
| **Part Analytics** | Consumption trends, cost by part, most-used parts |
| **Request Analytics** | Request volume, approval rate, average response time |

### Using Analytics

1. Navigate to **Analytics** from the sidebar.
2. Select the dashboard type.
3. Use date range filters to narrow the time period.
4. Charts and tables update automatically.

---

## 16. Settings & Configuration

Navigate to **Settings** to configure system behavior.

### 16.1 General Settings

- **Company profile** — Name, address, logo
- **General preferences** — Date format, currency, work order auto-close settings
- **Currency** — Set the default currency for costs

### 16.2 Work Order Settings

- Configure required and optional fields for work orders
- Set default status and priority values
- Configure work order numbering (custom sequences)

### 16.3 Request Settings

- Configure the work request form fields
- Set which fields are required for requesters

### 16.4 Roles

- Create and manage custom roles
- Set granular permissions per entity type

### 16.5 Checklists

- Create reusable checklists with predefined tasks
- Assign checklists to preventive maintenance schedules or work orders

### 16.6 Workflows

Workflows automate actions based on conditions:

- **Trigger:** When a work order is created, status changes, etc.
- **Condition:** If priority is High, if category is Electrical, etc.
- **Action:** Auto-assign to a user/team, send notification, change priority, etc.

### 16.7 UI Configuration

- Toggle visibility of sidebar menu items
- Customize which modules appear based on your organization's needs

---

## 17. Import & Export

### 17.1 Importing Data

1. Navigate to **Settings → Import** (or the Import section).
2. Select the entity type: Work Orders, Assets, Locations, Parts, Meters, or Preventive Maintenances.
3. **Download the template** CSV file.
4. Fill in the template with your data.
5. Upload the completed CSV file.
6. Review the import preview and confirm.

### 17.2 Exporting Data

1. Navigate to the entity list (e.g., Work Orders).
2. Use the **Export** option.
3. Download the data as CSV or Excel.

Exportable entities: Work Orders, Assets, Locations, Parts, Meters.

---

## 18. Localization

Atlas CMMS supports **14 languages**:

| Language | Code |
|----------|------|
| English | en |
| Spanish | es |
| French | fr |
| German | de |
| Portuguese (Brazil) | pt_br |
| Italian | it |
| Dutch | nl |
| Polish | pl |
| Turkish | tr |
| Swedish | sv |
| Russian | ru |
| Hungarian | hu |
| Arabic | ar |
| Chinese (Simplified) | zh_cn |

### Changing Your Language

The language is automatically detected from your browser settings. You can manually change it from your user preferences or account settings. The selected language is saved locally and persists across sessions.

---

## 19. Administrator Guide

### 19.1 Initial Setup Checklist

1. **Configure company settings** — Set company name, address, and logo.
2. **Set up roles** — Create roles with appropriate permissions for different user types.
3. **Create locations** — Build your location hierarchy.
4. **Add assets** — Import or manually create your asset registry.
5. **Set up categories** — Define work order, asset, part, and meter categories.
6. **Invite users** — Send invitations to your team members.
7. **Create parts** — Add your spare parts inventory.
8. **Set up PMs** — Define preventive maintenance schedules.
9. **Configure workflows** — Automate common processes.
10. **Review settings** — Verify work order and request field configurations.

### 19.2 Managing Subscriptions

- View your current subscription plan under **Settings → Subscription**.
- Upgrade or downgrade plans as needed.
- Payment is managed through the Paddle payment platform.

### 19.3 Backup & Restore

Use the provided backup scripts for database and file storage:

- **Windows:** `scripts/backup/atlas-backup.ps1`
- **Linux/Mac:** `scripts/backup/atlas-backup.sh`

These scripts back up both the PostgreSQL database and MinIO file storage.

### 19.4 Accessing API Documentation

Swagger UI is available at:

```
http://localhost:8080/swagger-ui/index.html
```

This provides interactive documentation for all API endpoints.

---

## 20. Troubleshooting

### Common Issues

| Problem | Solution |
|---------|----------|
| **Cannot log in** | Verify your email and password. Use "Forgot Password" to reset. Check if your account is activated. |
| **Page shows "Unauthorized"** | Your JWT token may have expired. Log out and log back in. |
| **Work orders not auto-generating** | Check that the PM schedule is active and the Quartz scheduler is running. Verify the schedule's start date is in the past. |
| **Files not uploading** | Ensure MinIO is running and accessible. Check the `PUBLIC_MINIO_ENDPOINT` configuration. Verify file size is under 35MB. |
| **Email notifications not sending** | Verify `ENABLE_EMAIL_NOTIFICATIONS=true` in your environment. Check SMTP or SendGrid credentials. |
| **Slow performance** | Check PostgreSQL connection pool settings. Verify adequate server resources. Consider database indexing. |
| **Blank page after login** | Clear browser cache and cookies. Check that the `API_URL` frontend configuration points to the correct backend. |

### Getting Help

- **Swagger API Docs:** `http://localhost:8080/swagger-ui/index.html`
- **Health Check:** `GET http://localhost:8080/health-check` — verifies the API is running
- **MinIO Console:** `http://localhost:9001` — verify file storage is operational
- **Application Logs:** Check Docker container logs with `docker logs atlas-cmms-backend` or `docker logs atlas-cmms-frontend`
