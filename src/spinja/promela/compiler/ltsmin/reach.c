
static const size_t 	DB_INIT_SIZE = 4;
static const size_t 	DB_MAX_SIZE = 10;

typedef struct spinja_args_s {
	void			   *model;
	void (*callback)(void* arg, transition_info_t *transition_info, state_t *out);
	void 			   *arg;
	size_t 				outs;
	state_db_t 		   *seen;
	int 				pid;
	int 				real_group;
} spinja_args_t;

extern void dfs (spinja_args_t *args, transition_info_t *transition_info, state_t *state);
extern int spinja_get_successor_all2( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg, state_t *tmp, int atomic);

void
dfs_cb(void* arg, transition_info_t *transition_info, state_t *out)
{
	spinja_args_t *args = (spinja_args_t *)arg;
	if (leaves_atomic[transition_info->group]) {
	    transition_info->group = args->real_group;
		args->callback (args->arg, transition_info, out);
		args->outs++;
	} else {
		dfs (args, transition_info, out);
	}
}

void
dfs (spinja_args_t *args, transition_info_t *transition_info, state_t *state)
{
	int result = state_db_lookup(args->seen, (const int*)state);
	switch ( result ) {
	case false: { // new state
		state_t out;
		int count = spinja_get_successor_all2 (args->model, state, dfs_cb, args, &out, args->pid);
		if (count == 0) {
			printf ("Loss of atomicity!\n");	// loss of atomicity
			int a;
			for (a = 0; a < spinja_get_state_size(); a++)
				printf("%d, ",a);
			printf ("\n");
			args->callback (args->arg, transition_info, state);
			args->outs++;
		}
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
reach (void* model, transition_info_t *transition_info, state_t *in,
	   void (*callback)(void* arg, transition_info_t *transition_info, state_t *out),
	   void *arg, int pid) {
	spinja_args_t args;
	args.model = model;
	args.callback = callback;
	args.arg = arg;
	args.outs = 0;
	args.pid = pid;
	args.real_group = transition_info->group;
	args.seen = state_db_create (spinja_get_state_size(), DB_INIT_SIZE, DB_MAX_SIZE);
	dfs (&args, transition_info, in);
	state_db_free (args.seen);
	return args.outs;
}
