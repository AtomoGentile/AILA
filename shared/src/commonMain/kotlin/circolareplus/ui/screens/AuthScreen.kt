                            AuthField(
                                value = classCode,
                                onValueChange = { classCode = it.uppercase().replace(Regex("[\\s-]"), "").take(12) },
                                placeholder = "Codice classe",
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrectEnabled = false
                                )
                            )
