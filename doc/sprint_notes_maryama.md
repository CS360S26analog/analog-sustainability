# Sprint Planning & Review Notes — Maryam Ali (Dashboard)

## Sprint 1 — Week of March 24, 2026

### Planned
- Understand the existing codebase and HomeFragment layout
- Identify which UI elements needed IDs added for data binding
- Research how ViewModels and LiveData work in Android

### Completed
- Reviewed HomeFragment.java, fragment_home.xml, User.java, ActivityLog.java
- Added android:id attributes to all stat TextViews in fragment_home.xml
- Created DashboardViewModel.java with Firestore queries for user profile
  and activity logs
- Implemented CO2 calculation using per-activity constants
- Implemented category count calculation
- Updated HomeFragment.java to observe LiveData and display real data

### Notes
- google-services.json placed in app/ folder (not committed — in .gitignore)
- All hardcoded values in fragment_home.xml replaced with real Firestore data
- Outstanding: time period filter (weekly/monthly) not yet connected to UI

---

## Sprint 2 — Week of March 31, 2026

### Planned
- Connect DashboardViewModel to HomeFragment fully
- Ensure streak, points, CO2 all load from Firestore correctly
- Build and verify no compile errors
- Commit and push to maryama-dashboard branch
- Open Pull Request to main

### Completed
- Full build successful with no errors
- HomeFragment now displays real user name, streak, CO2 saved, and points
- DashboardViewModel fully documented with Javadoc on all public methods
- Header comments added to both DashboardViewModel.java and HomeFragment.java
- Sprint notes written and committed to repo

