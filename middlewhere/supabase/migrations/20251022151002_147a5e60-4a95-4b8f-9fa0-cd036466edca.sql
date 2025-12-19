-- Create user groups table
CREATE TABLE public.user_groups (
  id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  name text NOT NULL,
  description text,
  company_name text,
  max_users integer,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Create user group members table (many-to-many relationship)
CREATE TABLE public.user_group_members (
  id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  group_id uuid NOT NULL REFERENCES public.user_groups(id) ON DELETE CASCADE,
  joined_at timestamp with time zone NOT NULL DEFAULT now(),
  UNIQUE(user_id, group_id)
);

-- Enable RLS
ALTER TABLE public.user_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_group_members ENABLE ROW LEVEL SECURITY;

-- RLS Policies for user_groups
CREATE POLICY "Authenticated users can view all groups"
  ON public.user_groups FOR SELECT
  USING (true);

CREATE POLICY "Authenticated users can manage groups"
  ON public.user_groups FOR ALL
  USING (true)
  WITH CHECK (true);

-- RLS Policies for user_group_members
CREATE POLICY "Authenticated users can view all group members"
  ON public.user_group_members FOR SELECT
  USING (true);

CREATE POLICY "Authenticated users can manage group members"
  ON public.user_group_members FOR ALL
  USING (true)
  WITH CHECK (true);

-- Add triggers for updated_at
CREATE TRIGGER update_user_groups_updated_at
  BEFORE UPDATE ON public.user_groups
  FOR EACH ROW
  EXECUTE FUNCTION public.update_updated_at_column();

-- Add group_id to profiles table for quick reference
ALTER TABLE public.profiles
ADD COLUMN group_id uuid REFERENCES public.user_groups(id) ON DELETE SET NULL;