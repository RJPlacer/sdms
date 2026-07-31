CREATE TABLE `verification_records` (
	`token` text PRIMARY KEY NOT NULL,
	`person_id` text NOT NULL,
	`person_type` text NOT NULL,
	`full_name` text NOT NULL,
	`school` text DEFAULT '' NOT NULL,
	`descriptor_label` text DEFAULT '' NOT NULL,
	`descriptor_value` text DEFAULT '' NOT NULL,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`photo_key` text,
	`issued_at` text DEFAULT CURRENT_TIMESTAMP NOT NULL,
	`updated_at` text DEFAULT CURRENT_TIMESTAMP NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `verification_records_person_id_unique` ON `verification_records` (`person_id`);