package com.rollinkxx.velum

import java.io.IOException

/** Penyimpanan aman gagal menulis atau membersihkan data secara durabel. */
class PersistenceException(message: String) : IOException(message)
