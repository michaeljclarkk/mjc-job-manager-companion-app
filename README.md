# MJC Job Manager Companion App

Official website: https://mjcjobmanager.com.au

<p align="center">
  <a href="https://mjcjobmanager.com.au">
	<img src="frontend/public/favicon.png" alt="MJC Job Manager" width="96" />
  </a>
</p>

This repository contains the native Android companion app for the MJC Job Manager platform. It’s designed for field workers to view assigned jobs, track time, and stay in sync with a self-hosted MJC Job Manager server.

## Features

- Connect to a self-hosted server
- Authenticate and manage sessions
- View assigned jobs and job details
- Track time on jobs (foreground service)
- Notifications
- Purchase orders

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- MVVM + clean architecture style
- Hilt (dependency injection)
- Retrofit + OkHttp
- Coroutines + Flow
- EncryptedSharedPreferences
- WorkManager + foreground services

## License

Licensed under the GNU Affero General Public License v3.0. See LICENSE.