package com.oprojectview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun NameSearchPreview() {
    MaterialTheme {
        Surface { // Provides a background for the preview
            val mockNames = listOf("Kotlin", "Java", "Swift", "Dart")
            NameSearchComponent(names = mockNames)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NameSearchComponent(names: List<String>) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val filteredNames = names.filter { it.contains(query, ignoreCase = true) }

    // Use the updated DockedSearchBar
    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { expanded = false },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                placeholder = { Text("Search names...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(16.dp)
    ) {
        // Results list (only shows when expanded)
        if (query.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(filteredNames) { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        modifier = Modifier.clickable {
                            query = name
                        }
                    )
                }
            }
        }
    }
}