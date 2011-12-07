
static const size_t 	DB_INIT_SIZE = 4;
static const size_t 	DB_MAX_SIZE = 10;

typedef struct spinja_args_s {
	void* model;
	void (*callback)(void* arg, transition_info_t *transition_info, state_t *out);
	void *arg;
	size_t outs;
	state_db_t *seen;
} spinja_args_t;

extern void dfs (spinja_args_t *args, state_t *state);
extern int spinja_get_successor_all2( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg, state_t *tmp, int atomic);

void
dfs_cb(void* arg, transition_info_t *transition_info, state_t *out)
{
	spinja_args_t *args = (spinja_args_t *)arg;
	if (leaves_atomic[transition_info->group]) {
		args->callback (args->arg, transition_info, out);
		args->outs++;
	} else {
		dfs (args, out);
	}
}

void
dfs (spinja_args_t *args, state_t *state)
{
	int result = state_db_lookup(args->seen, (const int*)state);
	switch ( result ) {
	case false: { // new state
		state_t out;
		spinja_get_successor_all2 (args->model, state, dfs_cb, args, &out, true);
		break;
	}
	case STATE_DB_FULL: // full database
		printf ("ERROR: model's internal atomic state database is filled (max size = 2^%zu). Increase DB_MAX_SIZE.", DB_MAX_SIZE);
		exit(1);
	case true: break; // seen state
	default: break;
	}
}

static inline int
reach (void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {
	spinja_args_t args;
	args.model = model;
	args.callback = callback;
	args.arg = arg;
	args.outs = 0;
	args.seen = state_db_create (spinja_get_state_size(), DB_INIT_SIZE, DB_MAX_SIZE);
	dfs (&args, in);
	state_db_free (args.seen);
	return args.outs;
}
