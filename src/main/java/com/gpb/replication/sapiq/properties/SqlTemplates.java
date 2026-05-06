package com.gpb.replication.sapiq.properties;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component("sqlTemplates")
public class SqlTemplates {

    /*
     * В SAP IQ schema фактически соответствует owner/user.
     * Берём только владельцев, у которых есть пользовательские таблицы/представления.
     */
    String schemaSql = """
            SELECT DISTINCT
                u.user_id AS oid,
                TRIM(u.user_name) AS schema_name
            FROM SYS.SYSUSER u
            JOIN SYS.SYSTAB t ON t.creator = u.user_id
            WHERE t.table_type IN (1, 2, 21)
              AND TRIM(u.user_name) NOT IN ('SYS', 'dbo', 'DBA')
            ORDER BY TRIM(u.user_name)
            """;

    String tableSql = """
            SELECT
                t.table_id,
                t.object_id AS oid,
                TRIM(u.user_name) AS schema_name,
                TRIM(t.table_name) AS table_name,
                CASE
                    WHEN t.table_type = 1 THEN 'REGULAR'
                    WHEN t.table_type = 2 THEN 'MATERIALIZED_VIEW'
                    WHEN t.table_type = 21 THEN 'VIEW'
                    ELSE 'OTHER'
                END AS table_type,
                CAST(r.remarks AS LONG VARCHAR) AS description,
                CAST(src.source AS LONG VARCHAR) AS view_definition
            FROM SYS.SYSTAB t
            JOIN SYS.SYSUSER u ON u.user_id = t.creator
            LEFT JOIN SYS.SYSREMARK r ON r.object_id = t.object_id
            LEFT JOIN SYS.SYSSOURCE src ON src.object_id = t.object_id
            WHERE t.table_type IN (1, 2, 21)
              AND TRIM(u.user_name) NOT IN ('SYS', 'dbo', 'DBA')
            ORDER BY TRIM(u.user_name), TRIM(t.table_name)
            """;

    String columnSql = """
            SELECT
                c.table_id,
                c.column_id AS ordinal_position,
                TRIM(c.column_name) AS column_name,
                UPPER(REPLACE(TRIM(d.domain_name), ' ', '_')) AS data_type,
                TRIM(c.base_type_str) AS data_type_display,
                CASE
                    WHEN d.domain_name IN ('char', 'varchar', 'long varchar', 'binary', 'varbinary', 'long binary')
                        THEN c.width
                    WHEN d.domain_name IN ('numeric', 'decimal')
                        THEN c.width
                    ELSE NULL
                END AS data_length,
                CASE
                    WHEN c.nulls = 'N' THEN 'NOT_NULL'
                    ELSE NULL
                END AS column_constraint,
                CAST(r.remarks AS LONG VARCHAR) AS description
            FROM SYS.SYSTABCOL c
            JOIN SYS.SYSTAB t ON t.table_id = c.table_id
            JOIN SYS.SYSUSER u ON u.user_id = t.creator
            JOIN SYS.SYSDOMAIN d ON d.domain_id = c.domain_id
            LEFT JOIN SYS.SYSREMARK r ON r.object_id = c.object_id
            WHERE t.table_type IN (1, 2, 21)
              AND TRIM(u.user_name) NOT IN ('SYS', 'dbo', 'DBA')
            ORDER BY c.table_id, c.column_id
            """;

    String constraintSql = """
            SELECT
                i.table_id,
                i.index_id,
                CASE
                    WHEN i.index_category = 1 THEN 'PRIMARY_KEY'
                    WHEN i.index_category = 2 THEN 'FOREIGN_KEY'
                    WHEN i.index_category = 3 AND i."unique" IN (1, 2, 5) THEN 'UNIQUE'
                    ELSE 'OTHER'
                END AS constraint_type,
                ic.sequence AS column_sequence,
                TRIM(c.column_name) AS column_name
            FROM SYS.SYSIDX i
            JOIN SYS.SYSIDXCOL ic
              ON ic.table_id = i.table_id
             AND ic.index_id = i.index_id
            JOIN SYS.SYSTABCOL c
              ON c.table_id = ic.table_id
             AND c.column_id = ic.column_id
            JOIN SYS.SYSTAB t ON t.table_id = i.table_id
            JOIN SYS.SYSUSER u ON u.user_id = t.creator
            WHERE t.table_type IN (1, 2, 21)
              AND TRIM(u.user_name) NOT IN ('SYS', 'dbo', 'DBA')
              AND i.index_category IN (1, 2, 3)
              AND (
                    i.index_category IN (1, 2)
                    OR i."unique" IN (1, 2, 5)
                  )
            ORDER BY i.table_id, i.index_id, ic.sequence
            """;
}