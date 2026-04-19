package dev.hossain.syntaxhighlight.data.samples

import dev.hossain.shiki.model.Language

/** A named code snippet paired with its [language] identifier. */
data class CodeSample(
    val label: String,
    val language: String,
    val code: String,
)

/** Hardcoded code samples used for showcasing syntax highlighting. */
object CodeSamples {
    val all: List<CodeSample> =
        listOf(
            CodeSample(
                label = "Kotlin",
                language = Language.KOTLIN,
                code =
                    """
                    package com.example

                    import kotlinx.coroutines.*

                    data class User(val name: String, val age: Int)

                    suspend fun fetchUsers(): List<User> = coroutineScope {
                        val users = listOf(
                            User("Alice", 30),
                            User("Bob", 25),
                            User("Charlie", 35),
                        )
                        val adults = users.filter { it.age >= 30 }
                        adults.forEach { user ->
                            println("${'$'}{user.name} is ${'$'}{user.age} years old")
                        }
                        adults
                    }

                    fun main() = runBlocking {
                        val result = fetchUsers()
                        println("Found ${'$'}{result.size} adults")
                    }
                    """.trimIndent(),
            ),
            CodeSample(
                label = "Python",
                language = Language.PYTHON,
                code =
                    """
                    from dataclasses import dataclass
                    from typing import Optional
                    import asyncio

                    @dataclass
                    class User:
                        name: str
                        age: int
                        email: Optional[str] = None

                    async def fetch_users() -> list[User]:
                        # Simulate async data fetch
                        await asyncio.sleep(0.1)
                        return [
                            User("Alice", 30, "alice@example.com"),
                            User("Bob", 25),
                            User("Charlie", 35, "charlie@example.com"),
                        ]

                    async def main():
                        users = await fetch_users()
                        adults = [u for u in users if u.age >= 30]
                        for user in adults:
                            print(f"{user.name} is {user.age} years old")

                    if __name__ == "__main__":
                        asyncio.run(main())
                    """.trimIndent(),
            ),
            CodeSample(
                label = "JSON",
                language = Language.JSON,
                code =
                    """
                    {
                      "users": [
                        {
                          "id": "usr_01",
                          "name": "Alice",
                          "age": 30,
                          "email": "alice@example.com",
                          "roles": ["admin", "viewer"],
                          "active": true
                        },
                        {
                          "id": "usr_02",
                          "name": "Bob",
                          "age": 25,
                          "email": null,
                          "roles": ["viewer"],
                          "active": false
                        }
                      ],
                      "meta": {
                        "total": 2,
                        "page": 1,
                        "perPage": 20
                      }
                    }
                    """.trimIndent(),
            ),
            CodeSample(
                label = "JavaScript",
                language = Language.JAVASCRIPT,
                code =
                    """
                    const fetchUsers = async (baseUrl) => {
                      const response = await fetch(`${'$'}{baseUrl}/users`);
                      if (!response.ok) {
                        throw new Error(`HTTP error! status: ${'$'}{response.status}`);
                      }
                      return response.json();
                    };

                    class UserService {
                      constructor(baseUrl) {
                        this.baseUrl = baseUrl;
                        this.cache = new Map();
                      }

                      async getUser(id) {
                        if (this.cache.has(id)) {
                          return this.cache.get(id);
                        }
                        const users = await fetchUsers(this.baseUrl);
                        const user = users.find(u => u.id === id);
                        this.cache.set(id, user);
                        return user;
                      }
                    }

                    export default UserService;
                    """.trimIndent(),
            ),
        )
}
