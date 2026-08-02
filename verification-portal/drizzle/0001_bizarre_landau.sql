CREATE TABLE `scanner_events` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`session_id` text NOT NULL,
	`payload` text NOT NULL,
	`created_at` text NOT NULL,
	`consumed_at` text,
	FOREIGN KEY (`session_id`) REFERENCES `scanner_sessions`(`session_id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `scanner_events_session_id_idx` ON `scanner_events` (`session_id`,`id`);--> statement-breakpoint
CREATE TABLE `scanner_sessions` (
	`session_id` text PRIMARY KEY NOT NULL,
	`desktop_secret_hash` text NOT NULL,
	`phone_secret_hash` text,
	`created_at` text NOT NULL,
	`expires_at` text NOT NULL,
	`paired_at` text,
	`last_activity_at` text NOT NULL
);
--> statement-breakpoint
CREATE INDEX `scanner_sessions_expires_at_idx` ON `scanner_sessions` (`expires_at`);