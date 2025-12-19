-- Create user groups enum
CREATE TYPE public.user_group AS ENUM ('vip', 'premium', 'basic', 'trial', 'free');

-- Create user status enum
CREATE TYPE public.user_status AS ENUM ('active', 'inactive', 'suspended', 'pending');

-- Create profiles table
CREATE TABLE public.profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  username TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  avatar_url TEXT,
  user_group public.user_group NOT NULL DEFAULT 'free',
  status public.user_status NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create user_subscriptions table
CREATE TABLE public.user_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  package_id INTEGER NOT NULL,
  package_name TEXT NOT NULL,
  start_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  end_date TIMESTAMP WITH TIME ZONE,
  auto_renew BOOLEAN NOT NULL DEFAULT false,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Create user_streaming_status table
CREATE TABLE public.user_streaming_status (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  channel_name TEXT,
  device_info TEXT,
  ip_address TEXT,
  started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  last_active TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  is_active BOOLEAN NOT NULL DEFAULT true,
  UNIQUE(user_id, channel_name, device_info)
);

-- Create user_activity_logs table
CREATE TABLE public.user_activity_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  activity_type TEXT NOT NULL,
  description TEXT,
  ip_address TEXT,
  user_agent TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Enable Row Level Security
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_streaming_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_activity_logs ENABLE ROW LEVEL SECURITY;

-- Profiles policies (users can read their own, only authenticated can see list)
CREATE POLICY "Users can view their own profile"
  ON public.profiles FOR SELECT
  USING (auth.uid() = id);

CREATE POLICY "Users can update their own profile"
  ON public.profiles FOR UPDATE
  USING (auth.uid() = id);

CREATE POLICY "Public profiles are viewable by authenticated users"
  ON public.profiles FOR SELECT
  TO authenticated
  USING (true);

-- Subscriptions policies
CREATE POLICY "Users can view their own subscriptions"
  ON public.user_subscriptions FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Authenticated users can view all subscriptions"
  ON public.user_subscriptions FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Authenticated users can manage subscriptions"
  ON public.user_subscriptions FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- Streaming status policies
CREATE POLICY "Users can view their own streaming status"
  ON public.user_streaming_status FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Authenticated users can view all streaming status"
  ON public.user_streaming_status FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Authenticated users can manage streaming status"
  ON public.user_streaming_status FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- Activity logs policies
CREATE POLICY "Users can view their own activity logs"
  ON public.user_activity_logs FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "Authenticated users can view all logs"
  ON public.user_activity_logs FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Authenticated users can create logs"
  ON public.user_activity_logs FOR INSERT
  TO authenticated
  WITH CHECK (true);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

-- Triggers for updated_at
CREATE TRIGGER update_profiles_updated_at
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at
  BEFORE UPDATE ON public.user_subscriptions
  FOR EACH ROW
  EXECUTE FUNCTION public.update_updated_at_column();

-- Function to handle new user signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, username, email, user_group, status)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'username', split_part(NEW.email, '@', 1)),
    NEW.email,
    'free',
    'active'
  );
  RETURN NEW;
END;
$$;

-- Trigger to create profile on user signup
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_user();

-- Create indexes for performance
CREATE INDEX idx_profiles_status ON public.profiles(status);
CREATE INDEX idx_profiles_group ON public.profiles(user_group);
CREATE INDEX idx_subscriptions_user ON public.user_subscriptions(user_id);
CREATE INDEX idx_subscriptions_active ON public.user_subscriptions(is_active);
CREATE INDEX idx_streaming_user ON public.user_streaming_status(user_id);
CREATE INDEX idx_streaming_active ON public.user_streaming_status(is_active);
CREATE INDEX idx_activity_user ON public.user_activity_logs(user_id);
CREATE INDEX idx_activity_created ON public.user_activity_logs(created_at DESC);