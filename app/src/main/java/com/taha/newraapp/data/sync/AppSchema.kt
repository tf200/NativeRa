package com.taha.newraapp.data.sync

import com.powersync.db.schema.Column
import com.powersync.db.schema.Index
import com.powersync.db.schema.IndexedColumn
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

/**
 * PowerSync Schema Definition
 * Define all tables that will be synced locally.
 * 
 * Note: PowerSync automatically handles the 'id' column.
 * Only define additional columns here.
 */
val AppSchema: Schema = Schema(
    listOf(
        // Users table - matches Prisma User model
        Table(
            name = "User",
            columns = listOf(
                Column.text("officerId"),
                Column.text("firstName"),
                Column.text("lastName"),
                Column.text("center"),
                Column.text("role"),
                Column.text("phoneNumber"),
                Column.text("password"),        // Added to match Angular schema
                Column.integer("isValid"),      // Boolean stored as 0/1
                Column.integer("isFrozen"),     // Boolean stored as 0/1
                Column.text("contacts")          // UUID array stored as JSON text
            ),
            indexes = listOf(
                // Index on officerId for efficient lookups
                Index("officerId_idx", listOf(IndexedColumn.ascending("officerId")))
            )
        )
        // Add more tables as needed (Device, Session, Message, etc.)
    )
)

