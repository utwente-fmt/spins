#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

#undef get16bits
#if (defined(__GNUC__) && defined(__i386__)) || defined(__WATCOMC__) \
  || defined(_MSC_VER) || defined (__BORLANDC__) || defined (__TURBOC__)
#define get16bits(d) (*((const uint16_t *) (d)))
#endif

#if !defined (get16bits)
#define get16bits(d) ((((uint32_t)(((const uint8_t *)(d))[1])) << 8)\
                       +(uint32_t)(((const uint8_t *)(d))[0]) )
#endif

uint32_t
hash (const void *data_, int len, uint32_t hash)
{
    const unsigned char *data = data_;
    uint32_t tmp;
    int rem;

    if (len <= 0 || data == NULL) return 0;

    rem = len & 3;
    len >>= 2;

    /* Main loop */
    for (;len > 0; len--) {
        hash  += get16bits (data);
        tmp    = (get16bits (data+2) << 11) ^ hash;
        hash   = (hash << 16) ^ tmp;
        data  += 2*sizeof (uint16_t);
        hash  += hash >> 11;
    }

    /* Handle end cases */
    switch (rem) {
        case 3: hash += get16bits (data);
                hash ^= hash << 16;
                hash ^= data[sizeof (uint16_t)] << 18;
                hash += hash >> 11;
                break;
        case 2: hash += get16bits (data);
                hash ^= hash << 11;
                hash += hash >> 17;
                break;
        case 1: hash += *data;
                hash ^= hash << 10;
                hash += hash >> 1;
    }

    /* Force "avalanching" of final 127 bits */
    hash ^= hash << 3;
    hash += hash >> 5;
    hash ^= hash << 4;
    hash += hash >> 17;
    hash ^= hash << 25;
    hash += hash >> 6;

    return hash;
}

typedef struct state_db_s state_db_t;

struct state_db_s {
    size_t              length;
    size_t              bytes;
    size_t              size;
    size_t              mask;
    int                *data;
    uint16_t           *hash;
};

#define EMPTY 0
#define CACHE_LINE 6
static const size_t CACHE_LINE_SIZE = 1 << CACHE_LINE;
static const size_t CACHE_LINE_INT16_SIZE = (1 << CACHE_LINE) / sizeof (uint16_t);
static const size_t CACHE_LINE_INT16_MASK = -((1 << CACHE_LINE) / sizeof (uint16_t));

int
state_db_lookup (const state_db_t *dbs, const int *v)
{
	int 				i;
    size_t              seed = 0;
    size_t              l = dbs->length;
    size_t              b = dbs->bytes;
    uint32_t            h = hash ((char *)v, b, 0);
    while (seed < dbs->size) {
        size_t              ref = h & dbs->mask;
        uint16_t			mem = h >> 16;
        size_t              line_begin = ref & CACHE_LINE_INT16_MASK;
        size_t              line_end = line_begin + CACHE_LINE_INT16_SIZE;
        for (i = 0; i < CACHE_LINE_INT16_SIZE; i++) {
            uint16_t           *hbucket = &dbs->hash[ref];
			int           	   *bucket = &dbs->data[ref * l];
            if (EMPTY == *hbucket) {
				*hbucket = mem;
				*hbucket |= 1;
				memcpy (bucket, v, b);
                return 0;
			}
            if ((mem | 1 == *hbucket) && 0 == memcmp (bucket, v, b) )
				return 1;
            ref += 1;
            ref = ref == line_end ? line_begin : ref;
        }
        h = hash ((char *)v, b, h + (seed++));
    }
    printf ("full\n");
    exit (1);
}

int
state_db_clear (const state_db_t *dbs)
{
	memset (dbs->hash, 0, sizeof (uint16_t[dbs->size]));
}

state_db_t *
state_db_create (int length, int size)
{
    state_db_t           *dbs;
    posix_memalign ((void **)&dbs, CACHE_LINE_SIZE, sizeof (state_db_t));
    dbs->length = length;
    dbs->bytes = length * sizeof (int);
    dbs->size = 1UL << size;
    dbs->mask = dbs->size - 1;
    posix_memalign((void **)&dbs->data, CACHE_LINE_SIZE, sizeof (int[dbs->size * length]));
    posix_memalign((void **)&dbs->hash, CACHE_LINE_SIZE, sizeof (uint16_t[dbs->size]));
	state_db_clear (dbs);
    return dbs;
}

void
state_db_free (state_db_t *dbs)
{
    free (dbs->data);
    free (dbs->hash);
    free (dbs);
}
