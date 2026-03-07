# Atlas CMMS — User Manual

> **Version:** 1.1  
> **Last Updated:** March 6, 2026

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Getting Started](#2-getting-started)
3. [How Atlas CMMS Works — Setup Workflow](#3-how-atlas-cmms-works--setup-workflow)
4. [Dashboard & Navigation](#4-dashboard--navigation)
5. [Settings & Configuration](#5-settings--configuration) *(start here)*
6. [Locations](#6-locations)
7. [People & Teams](#7-people--teams)
8. [Vendors & Customers](#8-vendors--customers)
9. [Inventory (Parts & Sets)](#9-inventory-parts--sets)
10. [Assets](#10-assets)
11. [Meters & Readings](#11-meters--readings)
12. [Work Orders](#12-work-orders)
13. [Work Requests](#13-work-requests)
14. [Preventive Maintenance](#14-preventive-maintenance)
15. [Purchase Orders](#15-purchase-orders)
16. [Analytics](#16-analytics)
17. [Files](#17-files)
18. [Import & Export](#18-import--export)
19. [Localization](#19-localization)
20. [Administrator Guide](#20-administrator-guide)
21. [Troubleshooting](#21-troubleshooting)

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

## 3. How Atlas CMMS Works — Setup Workflow

> **Read this first.** The modules in Atlas CMMS depend on each other. Setting things up in the right order saves time and avoids missing data later.

### 3.1 The Big Picture

Think of Atlas CMMS as a pyramid. Each layer needs the layer below it before it can work properly:

```
          ┌───────────────────────┐
          │     Analytics         │  ← review performance (needs history)
          ├───────────────────────┤
          │ Preventive Maintenance│  ← schedule recurring work (needs assets + people)
          ├───────────────────────┤
          │  Work Orders          │  ← day-to-day maintenance tasks
          │  Work Requests        │    (needs assets, people, parts)
          ├───────────────────────┤
          │  Assets & Meters      │  ← your equipment (needs locations, parts, people)
          ├───────────────────────┤
          │  Parts / Inventory    │  ← spare parts (needs vendors, locations)
          ├───────────────────────┤
          │  Locations            │  ← where things are
          │  People & Teams       │  ← who does the work
          │  Vendors & Contractors│  ← who supplies parts/services
          ├───────────────────────┤
          │  Settings, Roles,     │  ← system foundation
          │  Categories           │
          └───────────────────────┘
```

### 3.2 Recommended Setup Order

Follow these steps when setting up Atlas CMMS for the first time. Each step unlocks the one after it.

| Step | What to Do | Why It Comes First |
|------|------------|-------------------|
| **1. Settings** | Configure company profile, roles, and permissions | Everything else depends on having roles and basic config in place |
| **2. Categories** | Create categories for work orders, assets, parts, and meters | Categories classify all other records |
| **3. Locations** | Build your location hierarchy (sites, buildings, floors, rooms) | Assets need a location; parts need a storage location |
| **4. People & Teams** | Invite users, create teams, assign roles | Work orders and PMs need someone to assign work to |
| **5. Vendors** | Add your external suppliers and contractors | Parts reference vendors; purchase orders need vendors |
| **6. Parts / Inventory** | Add spare parts with quantities, costs, and vendors | Work orders consume parts; assets list associated parts |
| **7. Assets** | Register equipment with locations, linked parts, and assigned teams | Work orders and PMs are created against assets |
| **8. Meters** | Attach meters to assets (e.g., operating hours, mileage) | Meter-based PM triggers need meter data |
| **9. Work Orders** | Create and manage maintenance tasks | The core of daily operations |
| **10. Preventive Maintenance** | Schedule recurring work orders on assets | Automatically generates work orders on a schedule |
| **11. Analytics** | Review dashboards and reports | Needs historical work order and asset data |

### 3.3 What Depends on What — Quick Reference

Use this table when you try to create something and wonder "what do I need first?"

| I want to create… | I need these first |
|-------------------|--------------------|
| **A Location** | Nothing (or a parent location for nesting) |
| **A Person / User** | A role to assign them |
| **A Vendor** | Nothing |
| **A Part** | A vendor (optional), a storage location (optional) |
| **An Asset** | A location (recommended), parts to associate (optional) |
| **A Meter** | An asset to attach it to |
| **A Work Order** | An asset or location (recommended), a person/team to assign, parts if needed |
| **A Work Request** | Nothing (anyone can submit; approval creates a work order) |
| **A PM Schedule** | An asset, an assigned person/team, optionally a meter trigger |
| **A Purchase Order** | A vendor, the parts you want to order |

### 3.4 Typical Day-to-Day Workflow

Once the system is set up, the daily workflow looks like this:

1. **Work requests come in** — Non-maintenance staff submit requests describing what needs fixing.
2. **Managers review requests** — Approve (creates a work order automatically) or reject.
3. **Work orders are assigned** — Technicians see their assigned work orders with instructions, parts, and checklists.
4. **Technicians do the work** — Update status (Open → In Progress → Complete), log labor hours, record parts used.
5. **PM work orders auto-generate** — The system creates scheduled work orders based on time or meter readings.
6. **Inventory updates automatically** — Parts used on work orders decrement stock; low-stock alerts fire when needed.
7. **Managers review analytics** — Dashboards show completion rates, costs, asset reliability, and trends.

---

## 4. Dashboard & Navigation

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

> **Tip:** The sidebar order reflects how you *use* the system day-to-day (work orders front and center). But when *setting up* for the first time, follow the order in [Section 3](#3-how-atlas-cmms-works--setup-workflow) instead — start from the bottom of the sidebar and work your way up.

The sidebar can be collapsed for more screen space by clicking the toggle button.

---

## 5. Settings & Configuration *(Start Here)*

> **This is Step 1.** Configure the system foundation before adding any data.

Navigate to **Settings** to configure system behavior.

### 5.1 General Settings

- **Company profile** — Name, address, logo
- **General preferences** — Date format, currency, work order auto-close settings
- **Currency** — Set the default currency for costs

### 5.2 Roles

- Create and manage custom roles
- Set granular permissions per entity type
- You need roles before you can invite users (see [People & Teams](#7-people--teams))

### 5.3 Categories

- Define categories for work orders, assets, parts, and meters
- Categories help you classify and filter records throughout the system

### 5.4 Checklists

- Create reusable checklists with predefined tasks
- Assign checklists to preventive maintenance schedules or work orders

### 5.5 Work Order Settings

- Configure required and optional fields for work orders
- Set default status and priority values
- Configure work order numbering (custom sequences)

### 5.6 Request Settings

- Configure the work request form fields
- Set which fields are required for requesters

### 5.7 Workflows

Workflows automate actions based on conditions:

- **Trigger:** When a work order is created, status changes, etc.
- **Condition:** If priority is High, if category is Electrical, etc.
- **Action:** Auto-assign to a user/team, send notification, change priority, etc.

### 5.8 UI Configuration

- Toggle visibility of sidebar menu items
- Customize which modules appear based on your organization's needs

---

## 6. Locations

> **This is Step 2.** Define your physical spaces before registering assets or creating work orders.

Locations represent the physical places where assets are installed and work is performed.

### 6.1 Location Hierarchy

Locations support a parent-child hierarchy (e.g., Building → Floor → Room). Use **Children** to navigate through the tree.

### 6.2 Creating a Location

1. Navigate to **Locations**.
2. Click **+ (Add)**.
3. Enter name, description, address, and optionally select a parent location.
4. Upload a **floor plan** image (optional).
5. Click **Save**.

### What You Can Do After This

Once locations exist, you can:
- Assign assets to locations
- Specify storage locations for parts
- Associate work orders with a location

---

## 7. People & Teams

> **This is Step 3.** You need people in the system before you can assign work.

### 7.1 Managing Users

- Navigate to **People & Teams → People**.
- **Invite users** — Send email invitations with an assigned role.
- **Edit users** — Update name, role, phone, and other details.
- **Disable users** — Deactivate without deleting.

### 7.2 Managing Teams

- **Create teams** — Group users into teams (e.g., "Electrical Team").
- **Assign to work orders** — Teams can be assigned to work orders alongside or instead of individuals.

### 7.3 Roles & Permissions

Each user is assigned a **role** that controls what they can see and do:

| Permission Type | Description |
|----------------|-------------|
| **View** | Can see records (own only or all) |
| **Create** | Can create new records |
| **Edit Others** | Can modify records created by other users |
| **Delete Others** | Can remove records created by other users |

Permissions are set per entity type (work orders, assets, parts, etc.).

### What You Can Do After This

Once people and teams exist, you can:
- Assign work orders and PM schedules to them
- Assign assets to responsible users/teams
- Grant different access levels via roles

---

## 8. Vendors & Customers

> **This is Step 4.** Add vendors before creating parts (parts reference their supplier) and before purchase orders.

### Vendors

Manage external vendors who supply parts or services. Each vendor can have contact information, a company name, and be linked to purchase orders and parts.

### Customers

Track customers whose assets your organization maintains. Customers can be linked to assets and locations.

### What You Can Do After This

Once vendors exist, you can:
- Link vendors to parts (so you know who supplies each part)
- Create purchase orders to reorder from a vendor

---

## 9. Inventory (Parts & Sets)

> **This is Step 5.** Add parts before creating assets (assets can list associated parts) and before work orders (work orders consume parts).

### 9.1 Parts

Parts are spare parts and materials used in maintenance work.

- **View parts** — Navigate to **Inventory → Parts**.
- **Create a part** — Add name, description, quantity, cost, minimum quantity, category, vendors, and storage location.
- **Track usage** — Part quantities are automatically decremented when used in work orders.
- **Low stock alerts** — Set minimum quantities to get notified when stock is low.

### 9.2 Part Sets

Part sets are predefined groups of parts commonly used together, making it easy to add multiple parts to a work order at once.

### What You Can Do After This

Once parts exist, you can:
- Associate parts with assets (so you know which parts each asset needs)
- Add parts used to work orders (tracks consumption and costs)
- Create purchase orders to restock

---

## 10. Assets

> **This is Step 6.** Assets are the equipment you maintain. You need locations, people, and parts before assets are fully useful.

Assets represent the physical equipment and machinery that your organization maintains.

### 10.1 Viewing Assets

- Navigate to **Assets** from the sidebar.
- Assets are displayed in a list or grouped view.
- Click an asset to see its detail page.

### 10.2 Asset Detail Tabs

| Tab | Content |
|-----|---------|
| **Details** | Name, description, model, serial number, category, location, parent asset, custom fields |
| **Work Orders** | All work orders linked to this asset |
| **Parts** | Parts associated with this asset |
| **Files** | Documents, manuals, and photos |
| **Meters** | Meters attached to this asset with readings |
| **Downtimes** | Downtime history and duration |
| **Analytics** | Asset-specific cost and reliability analytics |

### 10.3 Creating an Asset

1. Click **+ (Add)**.
2. Fill in name, description, location, category, and other fields.
3. Optionally set a **parent asset** to create a hierarchy.
4. Attach files (manuals, photos).
5. Click **Save**.

### 10.4 Asset Lookup

Assets support **NFC tag** and **barcode** lookup for quick identification in the field.

### What You Can Do After This

Once assets exist, you can:
- Create work orders against them
- Schedule preventive maintenance for them
- Attach meters to track usage

---

## 11. Meters & Readings

> **This is Step 7.** Meters attach to assets. Create assets first.

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

### What You Can Do After This

Once meters exist, you can:
- Set up meter-based PM triggers (e.g., every 5,000 operating hours)
- Track asset usage trends in analytics

---

## 12. Work Orders

> **This is Step 8.** Work orders are the heart of the system. They depend on assets, people, and parts being set up first.

Work orders are the core of Atlas CMMS. They represent individual maintenance tasks that need to be completed.

### 12.1 Viewing Work Orders

- Navigate to **Work Orders** from the sidebar.
- The list view shows all work orders with columns for title, status, priority, assigned user, asset, due date, and more.
- Use the **search** bar and **column filters** to find specific work orders.
- Click any row to view full details.

### 12.2 Creating a Work Order

1. Click the **+ (Add)** button.
2. Fill in the required fields:
   - **Title** — Brief description of the work
   - **Priority** — None, Low, Medium, High
   - **Status** — Open (default)
   - **Assigned To** — User or Team *(set up in [People & Teams](#7-people--teams))*
   - **Asset** — Select the related asset *(set up in [Assets](#10-assets))*
   - **Location** — Select the location *(set up in [Locations](#6-locations))*
   - **Due Date** — When the work should be completed
   - **Description** — Detailed instructions
   - **Category** — Work order category *(set up in [Settings](#5-settings--configuration-start-here))*
3. Optionally add:
   - **Checklist tasks** — Step-by-step task list
   - **Files / Images** — Attach documents or photos
4. Click **Save**.

### 12.3 Managing a Work Order

| Action | How |
|--------|-----|
| **Change status** | Use the status dropdown (Open → In Progress → On Hold → Complete) |
| **Add labor** | Go to the Labor tab, log hours and hourly rate |
| **Add parts used** | Go to the Parts tab, select parts and quantities |
| **Add costs** | Go to the Additional Costs tab, enter extra expenses |
| **Add tasks** | Go to the Tasks tab, create or check off items |
| **Attach files** | Go to the Files tab, upload documents or photos |
| **View history** | Open the History tab to see all changes made |

### 12.4 Work Order Statuses

| Status | Meaning |
|--------|---------|
| **Open** | Newly created, not yet started |
| **In Progress** | Work is actively being performed |
| **On Hold** | Paused (e.g., waiting for parts) |
| **Complete** | Work finished |

### 12.5 Calendar View

Work orders can be displayed in a calendar view showing due dates and schedules. Switch between day, week, and month views.

---

## 13. Work Requests

> Work requests let non-maintenance staff ask for help. Approved requests automatically become work orders.

Requests allow non-maintenance personnel to submit work that needs to be done, which maintenance managers can then approve or reject.

### 13.1 Submitting a Request

1. Navigate to **Requests**.
2. Click **+ (Add)**.
3. Fill in the title, description, priority, asset, and location.
4. Click **Submit**.

### 13.2 Approving / Rejecting Requests

Managers see pending requests and can:

- **Approve** — Automatically creates a work order from the request.
- **Cancel** — Rejects the request with an optional reason.

---

## 14. Preventive Maintenance

> **This is Step 9.** PM schedules automatically create work orders. You need assets and people first.

Preventive Maintenance (PM) allows you to schedule recurring maintenance tasks that automatically generate work orders.

### 14.1 Creating a PM Schedule

1. Navigate to **Preventive Maintenance**.
2. Click **+ (Add)**.
3. Fill in:
   - **Title** — Name of the PM task
   - **Asset** / **Location** — What to maintain *(set up in [Assets](#10-assets) / [Locations](#6-locations))*
   - **Assigned To** — User or Team *(set up in [People & Teams](#7-people--teams))*
   - **Schedule** — Frequency (daily, weekly, monthly, yearly, or custom cron)
   - **Start Date** — When to begin scheduling
   - **Tasks** — Checklist of steps to perform
4. Click **Save**.

### 14.2 How It Works

- The system automatically creates work orders based on the PM schedule.
- Notifications are sent to assigned users before the due date.
- Meter-based triggers can also create work orders when a meter reading exceeds a threshold *(set up meters in [Meters & Readings](#11-meters--readings))*.

### 14.3 Viewing Recent Work Orders

Each PM entry shows its recently generated work orders so you can track completion rates.

---

## 15. Purchase Orders

> Purchase orders help you restock parts. You need vendors and parts set up first.

Purchase orders help you procure parts and materials from vendors.

### 15.1 Creating a Purchase Order

1. Navigate to **Purchase Orders**.
2. Click **+ (Add)** or **Create**.
3. Select a **vendor** *(set up in [Vendors & Customers](#8-vendors--customers))*.
4. Add **parts and quantities** *(set up in [Inventory](#9-inventory-parts--sets))*.
5. Set the due date and any notes.
6. Click **Save**.

### 15.2 Responding to a Purchase Order

Vendors can be notified and the PO status updated as:
- **Draft** → **Pending** → **Approved** → **Received**

When parts are received, inventory quantities can be updated.

---

## 16. Analytics

> Analytics give you insights into your operations. The more work orders you complete, the richer the data.

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

## 17. Files

The **Files** section provides a centralized file manager for all uploaded documents, images, and attachments.

- Files are stored in **MinIO** (S3-compatible storage) by default.
- Each file is scoped to your company.
- Files can be associated with work orders, assets, parts, and other entities.
- Supported operations: upload, rename, download, and delete.

---

## 18. Import & Export

### 18.1 Importing Data

> **Tip:** When importing, follow the same dependency order. Import locations before assets, import parts before work orders, etc.

1. Navigate to **Settings → Import** (or the Import section).
2. Select the entity type: Work Orders, Assets, Locations, Parts, Meters, or Preventive Maintenances.
3. **Download the template** CSV file.
4. Fill in the template with your data.
5. Upload the completed CSV file.
6. Review the import preview and confirm.

**Recommended import order:** Locations → People → Vendors → Parts → Assets → Meters → Work Orders → PMs

### 18.2 Exporting Data

1. Navigate to the entity list (e.g., Work Orders).
2. Use the **Export** option.
3. Download the data as CSV or Excel.

Exportable entities: Work Orders, Assets, Locations, Parts, Meters.

---

## 19. Localization

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

## 20. Administrator Guide

### 20.1 Initial Setup Checklist

Follow this order — it matches the dependency chain described in [Section 3](#3-how-atlas-cmms-works--setup-workflow):

| Step | Action | Where |
|------|--------|-------|
| 1 | Configure company profile (name, address, logo) | Settings → General |
| 2 | Create roles with appropriate permissions | Settings → Roles |
| 3 | Define categories for WOs, assets, parts, meters | Settings → Categories |
| 4 | Build your location hierarchy | Locations |
| 5 | Invite users and create teams | People & Teams |
| 6 | Add vendors and contractors | Vendors & Contractors |
| 7 | Add spare parts inventory | Inventory → Parts |
| 8 | Register assets with locations and linked parts | Assets |
| 9 | Attach meters to assets | Meters |
| 10 | Create preventive maintenance schedules | Preventive Maintenance |
| 11 | Configure workflows and automations | Settings → Workflows |
| 12 | Review work order and request field settings | Settings → WO / Request Settings |

### 20.2 Managing Subscriptions

- View your current subscription plan under **Settings → Subscription**.
- Upgrade or downgrade plans as needed.
- Payment is managed through the Paddle payment platform.

### 20.3 Backup & Restore

Use the provided backup scripts for database and file storage:

- **Windows:** `scripts/backup/atlas-backup.ps1`
- **Linux/Mac:** `scripts/backup/atlas-backup.sh`

These scripts back up both the PostgreSQL database and MinIO file storage.

### 20.4 Accessing API Documentation

Swagger UI is available at:

```
http://localhost:8080/swagger-ui/index.html
```

This provides interactive documentation for all API endpoints.

---

## 21. Troubleshooting

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
| **"I can't create a work order — dropdowns are empty"** | You need to add assets, locations, and people first. Follow the setup order in [Section 3](#3-how-atlas-cmms-works--setup-workflow). |

### Getting Help

- **Swagger API Docs:** `http://localhost:8080/swagger-ui/index.html`
- **Health Check:** `GET http://localhost:8080/health-check` — verifies the API is running
- **MinIO Console:** `http://localhost:9001` — verify file storage is operational
- **Application Logs:** Check Docker container logs with `docker logs atlas-cmms-backend` or `docker logs atlas-cmms-frontend`
